"""Train MIAE-style blind fusion on dataset_final.mat and save the fused cube.

The script is self-contained and intentionally does not use ``DataInfo`` from
the original repository because that class hard-codes the Chikusei paths and
creates a fake reference cube when HRHSI is absent.
"""

from __future__ import annotations

import argparse
import json
import math
import time
from contextlib import contextmanager
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional, Tuple, Union

import numpy as np
import scipy.io as sio
import torch
import torch.nn as nn
import torch.nn.functional as F


DEFAULT_INPUT = Path("dataset_final.mat")
DEFAULT_OUTPUT_ROOT = Path(__file__).resolve().parent / "data" / "result" / "registration_final"


@dataclass(frozen=True)
class NormalizationSpec:
    low: float
    high: float
    method: str

    @property
    def scale(self) -> float:
        return self.high - self.low


@contextmanager
def strict_fp32_backends(enabled: bool = True):
    """Temporarily disable TF32 so FP32 inference is numerically honest."""
    old_cudnn = torch.backends.cudnn.allow_tf32
    old_matmul = torch.backends.cuda.matmul.allow_tf32
    if enabled:
        torch.backends.cudnn.allow_tf32 = False
        torch.backends.cuda.matmul.allow_tf32 = False
    try:
        yield
    finally:
        torch.backends.cudnn.allow_tf32 = old_cudnn
        torch.backends.cuda.matmul.allow_tf32 = old_matmul


def validate_pair(lrhsi: np.ndarray, hrmsi: np.ndarray) -> int:
    """Validate the HWC input pair and return its integer spatial ratio."""
    if lrhsi.ndim != 3 or hrmsi.ndim != 3:
        raise ValueError("LRHSI and HRMSI must both be H x W x C arrays")
    if lrhsi.shape[0] <= 0 or lrhsi.shape[1] <= 0:
        raise ValueError("LRHSI spatial dimensions must be positive")

    ratio_h = hrmsi.shape[0] / lrhsi.shape[0]
    ratio_w = hrmsi.shape[1] / lrhsi.shape[1]
    ratio = int(round(ratio_h))
    if ratio < 1 or ratio_h != ratio or ratio_w != ratio:
        raise ValueError(
            "LRHSI and HRMSI must have the same integer isotropic spatial scale ratio"
        )
    if lrhsi.shape[2] < 2 or hrmsi.shape[2] < 1:
        raise ValueError("The input pair must contain spectral channels")
    return ratio


def validate_runtime_options(
    ratio: int,
    blind_iters: int,
    fusion_iters: int,
    batch_size: int,
    patch_hr: int,
    tile_size: int,
    halo_lr: int,
    log_every: int,
) -> None:
    """Reject invalid settings before creating an output directory or training."""
    positive = {
        "blind_iters": blind_iters,
        "fusion_iters": fusion_iters,
        "batch_size": batch_size,
        "patch_hr": patch_hr,
        "tile_size": tile_size,
        "log_every": log_every,
    }
    for name, value in positive.items():
        if value <= 0:
            raise ValueError(f"{name} must be positive")
    if patch_hr % ratio != 0:
        raise ValueError("patch_hr must be divisible by ratio")
    if tile_size % ratio != 0:
        raise ValueError("tile_size must be divisible by ratio")
    if patch_hr // ratio <= 2:
        raise ValueError("patch_hr is too small after excluding the blind-loss border")
    if halo_lr < 0:
        raise ValueError("halo_lr cannot be negative")


def compute_normalization(
    array: np.ndarray,
    low_percentile: float = 0.1,
    high_percentile: float = 99.9,
    sample_stride: int = 1,
) -> NormalizationSpec:
    """Build one global robust normalization without altering band ratios."""
    if not 0 <= low_percentile < high_percentile <= 100:
        raise ValueError("Normalization percentiles must satisfy 0 <= low < high <= 100")
    if sample_stride < 1:
        raise ValueError("sample_stride must be at least 1")

    sample = np.asarray(array[::sample_stride, ::sample_stride, :], dtype=np.float32)
    if not np.isfinite(sample).all():
        raise ValueError("Input contains NaN or Inf values")
    observed_min = float(sample.min())
    observed_max = float(sample.max())
    if observed_min >= 0.0 and observed_max <= 1.0:
        return NormalizationSpec(0.0, 1.0, "already_0_1")

    low, high = np.percentile(sample, [low_percentile, high_percentile])
    low = float(low)
    high = float(high)
    if not math.isfinite(low) or not math.isfinite(high) or high <= low:
        raise ValueError("Cannot derive a non-degenerate normalization range")
    return NormalizationSpec(
        low=low,
        high=high,
        method=f"global_percentile_{low_percentile:g}_{high_percentile:g}",
    )


def apply_normalization(array: np.ndarray, spec: NormalizationSpec) -> np.ndarray:
    """Apply a global affine transform and clip to the model's [0, 1] range."""
    if spec.scale <= 0:
        raise ValueError("Normalization high must be greater than low")
    result = np.asarray(array, dtype=np.float32).copy()
    np.subtract(result, np.float32(spec.low), out=result)
    np.multiply(result, np.float32(1.0 / spec.scale), out=result)
    np.clip(result, 0.0, 1.0, out=result)
    return result


def choose_hrmsi_normalization(hrmsi: np.ndarray) -> NormalizationSpec:
    """Use exact 8-bit scaling when applicable; otherwise use robust scaling."""
    finite_min = float(np.min(hrmsi))
    finite_max = float(np.max(hrmsi))
    if finite_min >= 0.0 and 1.0 < finite_max <= 255.0:
        return NormalizationSpec(0.0, 255.0, "unsigned_8bit_0_255")
    return compute_normalization(hrmsi, 0.1, 99.9, sample_stride=1)


def _to_nchw(array: np.ndarray) -> torch.Tensor:
    return torch.from_numpy(np.ascontiguousarray(array.transpose(2, 0, 1))).unsqueeze(0)


def sample_aligned_patches(
    lrhsi: torch.Tensor,
    hrmsi: torch.Tensor,
    ratio: int,
    patch_hr: int,
    batch_size: int,
    rng: np.random.Generator,
) -> Tuple[torch.Tensor, torch.Tensor]:
    """Sample spatially corresponding LRHSI/HRMSI patches."""
    if patch_hr % ratio != 0:
        raise ValueError("patch_hr must be divisible by ratio")
    patch_lr = patch_hr // ratio
    _, _, lr_h, lr_w = lrhsi.shape
    if patch_lr > lr_h or patch_lr > lr_w:
        raise ValueError("Patch is larger than the LRHSI image")

    max_y = lr_h - patch_lr
    max_x = lr_w - patch_lr
    ys = rng.integers(0, max_y + 1, size=batch_size)
    xs = rng.integers(0, max_x + 1, size=batch_size)
    lr_patches = []
    hr_patches = []
    for y, x in zip(ys.tolist(), xs.tolist()):
        lr_patches.append(lrhsi[:, :, y : y + patch_lr, x : x + patch_lr])
        hy, hx = y * ratio, x * ratio
        hr_patches.append(hrmsi[:, :, hy : hy + patch_hr, hx : hx + patch_hr])
    return torch.cat(lr_patches, dim=0), torch.cat(hr_patches, dim=0)


class BlindKernelEstimator(nn.Module):
    """Estimate non-negative, sum-to-one PSF and SRF kernels."""

    def __init__(self, hs_bands: int, ms_bands: int, ratio: int):
        super().__init__()
        self.hs_bands = hs_bands
        self.ms_bands = ms_bands
        self.ratio = ratio
        self.kernel_size = 2 * ratio - 1
        self.psf_logits = nn.Parameter(torch.zeros(1, 1, self.kernel_size, self.kernel_size))
        self.srf_logits = nn.Parameter(torch.zeros(ms_bands, hs_bands, 1, 1))
        # Independent sensor normalizations require a small radiometric bridge.
        # softplus(inv_softplus(1)) initializes every gain to exactly one.
        inverse_softplus_one = math.log(math.expm1(1.0))
        self.gain_logits = nn.Parameter(torch.full((1, ms_bands, 1, 1), inverse_softplus_one))
        self.bias_logits = nn.Parameter(torch.zeros(1, ms_bands, 1, 1))

    def kernels(self) -> Tuple[torch.Tensor, torch.Tensor]:
        psf = torch.softmax(self.psf_logits.flatten(), dim=0).reshape_as(self.psf_logits)
        srf = torch.softmax(self.srf_logits, dim=1)
        return psf, srf

    def calibration(self) -> Tuple[torch.Tensor, torch.Tensor]:
        gain = F.softplus(self.gain_logits)
        bias = 0.5 * torch.tanh(self.bias_logits)
        return gain, bias

    def forward(self, lrhsi: torch.Tensor, hrmsi: torch.Tensor):
        psf, srf = self.kernels()
        gain, bias = self.calibration()
        hsi_to_msi = gain * F.conv2d(lrhsi, srf) + bias
        psf_msi = psf.repeat(self.ms_bands, 1, 1, 1)
        msi_to_low = F.conv2d(
            hrmsi,
            psf_msi,
            padding=self.kernel_size // 2,
            groups=self.ms_bands,
        )[:, :, :: self.ratio, :: self.ratio]
        return hsi_to_msi, msi_to_low


def blind_consistency_loss(
    projected_hsi: torch.Tensor,
    downsampled_msi: torch.Tensor,
    border: int,
) -> torch.Tensor:
    """L1 consistency excluding patch pixels affected by artificial padding."""
    if border < 0:
        raise ValueError("border cannot be negative")
    if border == 0:
        return F.l1_loss(projected_hsi, downsampled_msi)
    if projected_hsi.shape[-2] <= 2 * border or projected_hsi.shape[-1] <= 2 * border:
        raise ValueError("border removes the complete blind-training patch")
    return F.l1_loss(
        projected_hsi[:, :, border:-border, border:-border],
        downsampled_msi[:, :, border:-border, border:-border],
    )


class FusionAutoencoder(nn.Module):
    """MIAE-style abundance encoder and learned endmember decoder."""

    def __init__(self, hs_bands: int, ms_bands: int, edm_num: int = 30, stages: int = 3):
        super().__init__()
        if stages < 1:
            raise ValueError("stages must be at least 1")
        self.hs_bands = hs_bands
        self.ms_bands = ms_bands
        self.edm_num = edm_num
        self.stages = stages
        self.edm = nn.Parameter(torch.full((hs_bands, edm_num, 1, 1), 1.0 / edm_num))
        self.hsi_encoder = nn.Sequential(
            nn.Conv2d(hs_bands, edm_num, 1),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Conv2d(edm_num, edm_num, 1),
            nn.LeakyReLU(0.2, inplace=True),
        )
        self.msi_encoder = nn.Sequential(
            nn.Conv2d(ms_bands, edm_num, 1),
            nn.LeakyReLU(0.2, inplace=True),
        )
        self.initial_fusion = nn.Sequential(
            nn.Conv2d(2 * edm_num, edm_num, 1),
            nn.LeakyReLU(0.2, inplace=True),
        )
        self.refine = nn.ModuleList()
        for _ in range(stages - 1):
            self.refine.append(
                nn.ModuleDict(
                    {
                        "pre": nn.Sequential(
                            nn.Conv2d(edm_num, edm_num, 1),
                            nn.LeakyReLU(0.2, inplace=True),
                        ),
                        "fuse": nn.Sequential(
                            nn.Conv2d(3 * edm_num, edm_num, 1),
                            nn.LeakyReLU(0.2, inplace=True),
                        ),
                    }
                )
            )
        self.apply(self._initialize)

    @staticmethod
    def _initialize(module: nn.Module) -> None:
        if isinstance(module, nn.Conv2d):
            fan_in = module.weight.shape[1]
            nn.init.trunc_normal_(module.weight, mean=0.0, std=math.sqrt(1.0 / fan_in))
            if module.bias is not None:
                nn.init.zeros_(module.bias)

    def forward(self, hsi_up: torch.Tensor, msi: torch.Tensor) -> torch.Tensor:
        hsi_features = self.hsi_encoder(hsi_up)
        msi_features = self.msi_encoder(msi)
        abundance = self.initial_fusion(torch.cat((hsi_features, msi_features), dim=1))
        for block in self.refine:
            refined = block["pre"](abundance)
            abundance = block["fuse"](
                torch.cat((refined, hsi_features, msi_features), dim=1)
            )
        abundance = torch.clamp(abundance, 0.0, 1.0)
        fused = F.conv2d(abundance, self.edm)
        return torch.clamp(fused, 0.0, 1.0)


def train_blind(
    lrhsi: torch.Tensor,
    hrmsi: torch.Tensor,
    ratio: int,
    iterations: int,
    patch_hr: int,
    batch_size: int,
    learning_rate: float,
    device: torch.device,
    rng: np.random.Generator,
    amp: bool,
    log_every: int,
):
    model = BlindKernelEstimator(lrhsi.shape[1], hrmsi.shape[1], ratio).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    scaler = torch.cuda.amp.GradScaler(enabled=amp and device.type == "cuda")
    losses = []
    model.train()
    for step in range(1, iterations + 1):
        hsi_patch, msi_patch = sample_aligned_patches(
            lrhsi, hrmsi, ratio, patch_hr, batch_size, rng
        )
        optimizer.zero_grad(set_to_none=True)
        with torch.cuda.amp.autocast(enabled=amp and device.type == "cuda"):
            hsi_to_msi, msi_to_low = model(hsi_patch, msi_patch)
            border = max(1, math.ceil((model.kernel_size // 2) / ratio))
            loss = blind_consistency_loss(hsi_to_msi, msi_to_low, border)
        scaler.scale(loss).backward()
        scaler.step(optimizer)
        scaler.update()
        losses.append(float(loss.detach().cpu()))
        if step == 1 or step % log_every == 0 or step == iterations:
            print(f"[Blind] {step:5d}/{iterations}: loss={losses[-1]:.7f}", flush=True)
    return model, losses


def _degrade_fused(
    fused: torch.Tensor,
    psf: torch.Tensor,
    srf: torch.Tensor,
    spectral_gain: torch.Tensor,
    spectral_bias: torch.Tensor,
    ratio: int,
):
    hs_bands = fused.shape[1]
    kernel_size = psf.shape[-1]
    psf_hsi = psf.repeat(hs_bands, 1, 1, 1)
    low_hsi = F.conv2d(
        fused,
        psf_hsi,
        padding=kernel_size // 2,
        groups=hs_bands,
    )[:, :, ::ratio, ::ratio]
    high_msi = spectral_gain * F.conv2d(fused, srf) + spectral_bias
    return low_hsi, high_msi


def train_fusion(
    lrhsi: torch.Tensor,
    hrmsi: torch.Tensor,
    ratio: int,
    psf: torch.Tensor,
    srf: torch.Tensor,
    spectral_gain: torch.Tensor,
    spectral_bias: torch.Tensor,
    edm_num: int,
    stages: int,
    iterations: int,
    patch_hr: int,
    batch_size: int,
    learning_rate: float,
    weight_decay: float,
    device: torch.device,
    rng: np.random.Generator,
    amp: bool,
    log_every: int,
):
    model = FusionAutoencoder(
        hs_bands=lrhsi.shape[1],
        ms_bands=hrmsi.shape[1],
        edm_num=edm_num,
        stages=stages,
    ).to(device)
    optimizer = torch.optim.Adam(
        model.parameters(), lr=learning_rate, weight_decay=weight_decay
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=max(iterations, 1), eta_min=learning_rate * 0.05
    )
    scaler = torch.cuda.amp.GradScaler(enabled=amp and device.type == "cuda")
    psf = psf.to(device)
    srf = srf.to(device)
    spectral_gain = spectral_gain.to(device)
    spectral_bias = spectral_bias.to(device)
    losses = []
    model.train()
    for step in range(1, iterations + 1):
        hsi_patch, msi_patch = sample_aligned_patches(
            lrhsi, hrmsi, ratio, patch_hr, batch_size, rng
        )
        optimizer.zero_grad(set_to_none=True)
        with torch.cuda.amp.autocast(enabled=amp and device.type == "cuda"):
            hsi_up = F.interpolate(
                hsi_patch, scale_factor=ratio, mode="bilinear", align_corners=False
            )
            fused = model(hsi_up, msi_patch)
            low_hsi, high_msi = _degrade_fused(
                fused, psf, srf, spectral_gain, spectral_bias, ratio
            )
            low_border = max(1, math.ceil((psf.shape[-1] // 2) / ratio))
            high_border = low_border * ratio
            loss_hsi = F.l1_loss(
                low_hsi[:, :, low_border:-low_border, low_border:-low_border],
                hsi_patch[:, :, low_border:-low_border, low_border:-low_border],
            )
            loss_msi = F.l1_loss(
                high_msi[:, :, high_border:-high_border, high_border:-high_border],
                msi_patch[:, :, high_border:-high_border, high_border:-high_border],
            )
            loss = loss_hsi + loss_msi
        scaler.scale(loss).backward()
        scaler.step(optimizer)
        scaler.update()
        scheduler.step()
        with torch.no_grad():
            model.edm.clamp_(0.0, 1.0)
        losses.append(float(loss.detach().cpu()))
        if step == 1 or step % log_every == 0 or step == iterations:
            lr = optimizer.param_groups[0]["lr"]
            print(
                f"[MIAE]  {step:5d}/{iterations}: loss={losses[-1]:.7f}, lr={lr:.3e}",
                flush=True,
            )
    return model, losses


def infer_tiled(
    model: nn.Module,
    lrhsi: torch.Tensor,
    hrmsi: torch.Tensor,
    ratio: int,
    tile_size: int,
    halo_lr: int,
    device: torch.device,
    amp: bool = False,
    output_memmap_path: Optional[Union[str, Path]] = None,
    srf: Optional[torch.Tensor] = None,
    spectral_gain: Optional[torch.Tensor] = None,
    spectral_bias: Optional[torch.Tensor] = None,
    synthesized_output_path: Optional[Union[str, Path]] = None,
) -> np.ndarray:
    """Run seam-safe tiled inference; optionally write NPY memmaps directly."""
    if tile_size % ratio != 0:
        raise ValueError("tile_size must be divisible by ratio")
    if (srf is None) != (synthesized_output_path is None):
        raise ValueError("srf and synthesized_output_path must be provided together")
    if srf is not None and (spectral_gain is None or spectral_bias is None):
        raise ValueError("spectral_gain and spectral_bias are required with srf")

    model = model.to(device)
    model.eval()
    _, _, hr_h, hr_w = hrmsi.shape
    _, _, lr_h, lr_w = lrhsi.shape
    if hr_h != lr_h * ratio or hr_w != lr_w * ratio:
        raise ValueError("Tensor shapes do not match ratio")

    output = None
    synthesized = None
    if srf is not None:
        srf = srf.to(device)
        spectral_gain = spectral_gain.to(device)
        spectral_bias = spectral_bias.to(device)
    context = torch.inference_mode if hasattr(torch, "inference_mode") else torch.no_grad
    tile_count = math.ceil(hr_h / tile_size) * math.ceil(hr_w / tile_size)
    tile_index = 0
    with strict_fp32_backends(enabled=device.type == "cuda" and not amp), context():
        for y0 in range(0, hr_h, tile_size):
            y1 = min(y0 + tile_size, hr_h)
            for x0 in range(0, hr_w, tile_size):
                x1 = min(x0 + tile_size, hr_w)
                lr_y0, lr_x0 = y0 // ratio, x0 // ratio
                lr_y1 = math.ceil(y1 / ratio)
                lr_x1 = math.ceil(x1 / ratio)
                py0 = max(0, lr_y0 - halo_lr)
                px0 = max(0, lr_x0 - halo_lr)
                py1 = min(lr_h, lr_y1 + halo_lr)
                px1 = min(lr_w, lr_x1 + halo_lr)
                hy0, hx0, hy1, hx1 = py0 * ratio, px0 * ratio, py1 * ratio, px1 * ratio
                hsi_patch = lrhsi[:, :, py0:py1, px0:px1].to(device, non_blocking=True)
                msi_patch = hrmsi[:, :, hy0:hy1, hx0:hx1].to(device, non_blocking=True)
                with torch.cuda.amp.autocast(enabled=amp and device.type == "cuda"):
                    hsi_up = F.interpolate(
                        hsi_patch,
                        scale_factor=ratio,
                        mode="bilinear",
                        align_corners=False,
                    )
                    prediction = model(hsi_up, msi_patch)
                cy0, cx0 = y0 - hy0, x0 - hx0
                cy1, cx1 = cy0 + (y1 - y0), cx0 + (x1 - x0)
                core = prediction[:, :, cy0:cy1, cx0:cx1].float()
                core_hwc = core[0].permute(1, 2, 0).cpu().numpy()
                if output is None:
                    shape = (hr_h, hr_w, core_hwc.shape[2])
                    if output_memmap_path is None:
                        output = np.empty(shape, dtype=np.float32)
                    else:
                        output = np.lib.format.open_memmap(
                            str(output_memmap_path), mode="w+", dtype=np.float32, shape=shape
                        )
                    if srf is not None:
                        synthesized = np.lib.format.open_memmap(
                            str(synthesized_output_path),
                            mode="w+",
                            dtype=np.float32,
                            shape=(hr_h, hr_w, srf.shape[0]),
                        )
                output[y0:y1, x0:x1, :] = core_hwc
                if srf is not None:
                    projected = (
                        spectral_gain * F.conv2d(core, srf) + spectral_bias
                    ).clamp(0.0, 1.0)
                    synthesized[y0:y1, x0:x1, :] = (
                        projected[0].permute(1, 2, 0).cpu().numpy()
                    )
                tile_index += 1
                print(f"[Infer] {tile_index:3d}/{tile_count} tiles", flush=True)
    if hasattr(output, "flush"):
        output.flush()
    if synthesized is not None and hasattr(synthesized, "flush"):
        synthesized.flush()
    return output


def synthesize_msi(hsi: np.ndarray, srf: np.ndarray, row_chunk: int = 64) -> np.ndarray:
    """Project every hyperspectral band through the full SRF matrix."""
    hsi = np.asarray(hsi, dtype=np.float32)
    srf = np.asarray(srf, dtype=np.float32)
    if hsi.ndim != 3 or srf.ndim != 2 or hsi.shape[2] != srf.shape[1]:
        raise ValueError("Expected HSI H x W x L and SRF M x L")
    result = np.empty((hsi.shape[0], hsi.shape[1], srf.shape[0]), dtype=np.float32)
    for y0 in range(0, hsi.shape[0], row_chunk):
        y1 = min(y0 + row_chunk, hsi.shape[0])
        result[y0:y1] = np.tensordot(hsi[y0:y1], srf.T, axes=([2], [0]))
    return result


def colorize_srf_msi(
    synthesized_msi: np.ndarray,
    reference_hrmsi: np.ndarray,
    low_percentile: float = 1.0,
    high_percentile: float = 99.0,
    gamma: float = 0.9,
    saturation: float = 1.05,
) -> np.ndarray:
    """Match synthesized MSI channel tones to HRMSI without mixing its pixels in."""
    synthesized_msi = np.asarray(synthesized_msi, dtype=np.float32)
    reference_hrmsi = np.asarray(reference_hrmsi, dtype=np.float32)
    if synthesized_msi.shape[-1] != 3 or reference_hrmsi.shape[-1] != 3:
        raise ValueError("RGB preview requires three SRF/MSI channels")
    if gamma <= 0 or saturation < 0:
        raise ValueError("gamma must be positive and saturation non-negative")

    mapped = np.empty_like(synthesized_msi, dtype=np.float32)
    for channel in range(3):
        source = synthesized_msi[..., channel]
        reference = reference_hrmsi[..., channel]
        source_low, source_high = np.percentile(
            source, [low_percentile, high_percentile]
        )
        reference_low, reference_high = np.percentile(
            reference, [low_percentile, high_percentile]
        )
        if source_high <= source_low:
            mapped[..., channel] = np.clip(reference.mean(), 0.0, 1.0)
        else:
            unit = np.clip((source - source_low) / (source_high - source_low), 0.0, 1.0)
            mapped[..., channel] = unit * (reference_high - reference_low) + reference_low
    mapped = np.clip(mapped, 0.0, 1.0)
    if saturation != 1.0:
        luminance = (
            0.299 * mapped[..., 0:1]
            + 0.587 * mapped[..., 1:2]
            + 0.114 * mapped[..., 2:3]
        )
        mapped = np.clip(luminance + saturation * (mapped - luminance), 0.0, 1.0)
    mapped = np.power(mapped, gamma)
    return np.rint(mapped * 255.0).astype(np.uint8)


def _save_png(path: Path, image: np.ndarray) -> None:
    from PIL import Image

    Image.fromarray(image, mode="RGB").save(path)


def _load_input(path: Path):
    if not path.is_file():
        raise FileNotFoundError(f"Input MAT file not found: {path}")
    data = sio.loadmat(path)
    missing = [name for name in ("LRHSI", "HRMSI") if name not in data]
    if missing:
        raise KeyError(f"Input MAT file is missing variables: {', '.join(missing)}")
    lrhsi = np.asarray(data["LRHSI"])
    hrmsi = np.asarray(data["HRMSI"])
    ratio = validate_pair(lrhsi, hrmsi)
    if not np.isfinite(lrhsi).all() or not np.isfinite(hrmsi).all():
        raise ValueError("LRHSI or HRMSI contains NaN/Inf")
    return lrhsi, hrmsi, ratio


def _save_json(path: Path, payload) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def _save_torch(path: Path, payload) -> None:
    """Save through a file object because older PyTorch builds reject Unicode paths on Windows."""
    with path.open("wb") as output:
        torch.save(payload, output)


def _parse_args():
    parser = argparse.ArgumentParser(
        description="Blind-train MIAE and fuse LRHSI with HRMSI using a 4090-friendly pipeline."
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--run-name", default=None)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--blind-iters", type=int, default=3000)
    parser.add_argument("--fusion-iters", type=int, default=5000)
    parser.add_argument("--blind-lr", type=float, default=5e-3)
    parser.add_argument("--fusion-lr", type=float, default=5e-3)
    parser.add_argument("--weight-decay", type=float, default=1e-3)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--patch-hr", type=int, default=64)
    parser.add_argument("--edm-num", type=int, default=30)
    parser.add_argument("--stages", type=int, default=3)
    parser.add_argument("--tile-size", type=int, default=256)
    parser.add_argument("--halo-lr", type=int, default=2)
    parser.add_argument("--hsi-low-percentile", type=float, default=0.1)
    parser.add_argument("--hsi-high-percentile", type=float, default=99.9)
    parser.add_argument("--normalization-sample-stride", type=int, default=4)
    parser.add_argument("--log-every", type=int, default=100)
    parser.add_argument("--no-amp", action="store_true")
    parser.add_argument(
        "--amp-inference",
        action="store_true",
        help="Opt into FP16 final inference; FP32 is the scientific-output default.",
    )
    parser.add_argument("--skip-mat", action="store_true")
    parser.add_argument(
        "--smoke-test",
        action="store_true",
        help="Use a centered 128 x 128 HR crop and two iterations per stage.",
    )
    return parser.parse_args()


def main() -> None:
    args = _parse_args()
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)
    rng = np.random.default_rng(args.seed)
    if args.device.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested but torch.cuda.is_available() is false")
    device = torch.device(args.device)
    amp = device.type == "cuda" and not args.no_amp

    lrhsi_raw, hrmsi_raw, ratio = _load_input(args.input)
    if args.smoke_test:
        crop_lr = min(64, lrhsi_raw.shape[0], lrhsi_raw.shape[1])
        y0 = (lrhsi_raw.shape[0] - crop_lr) // 2
        x0 = (lrhsi_raw.shape[1] - crop_lr) // 2
        lrhsi_raw = lrhsi_raw[y0 : y0 + crop_lr, x0 : x0 + crop_lr]
        hrmsi_raw = hrmsi_raw[
            y0 * ratio : (y0 + crop_lr) * ratio,
            x0 * ratio : (x0 + crop_lr) * ratio,
        ]
        args.blind_iters = 2
        args.fusion_iters = 2
        args.batch_size = min(args.batch_size, 2)
        args.patch_hr = min(args.patch_hr, crop_lr * ratio)
        args.tile_size = min(args.tile_size, crop_lr * ratio)

    validate_runtime_options(
        ratio=ratio,
        blind_iters=args.blind_iters,
        fusion_iters=args.fusion_iters,
        batch_size=args.batch_size,
        patch_hr=args.patch_hr,
        tile_size=args.tile_size,
        halo_lr=args.halo_lr,
        log_every=args.log_every,
    )
    if args.patch_hr // ratio > min(lrhsi_raw.shape[:2]):
        raise ValueError("patch_hr is larger than the available image area")

    hsi_spec = compute_normalization(
        lrhsi_raw,
        args.hsi_low_percentile,
        args.hsi_high_percentile,
        args.normalization_sample_stride,
    )
    msi_spec = choose_hrmsi_normalization(hrmsi_raw)
    lrhsi_np = apply_normalization(lrhsi_raw, hsi_spec)
    hrmsi_np = apply_normalization(hrmsi_raw, msi_spec)
    del lrhsi_raw, hrmsi_raw

    run_name = args.run_name or datetime.now().strftime(
        ("smoke_%Y%m%d_%H%M%S" if args.smoke_test else "%Y%m%d_%H%M%S")
    )
    output_dir = args.output_root / run_name
    output_dir.mkdir(parents=True, exist_ok=False)
    config = vars(args).copy()
    config["input"] = str(args.input)
    config["output_root"] = str(args.output_root)
    config.update(
        {
            "output_dir": str(output_dir),
            "ratio": ratio,
            "lrhsi_shape": list(lrhsi_np.shape),
            "hrmsi_shape": list(hrmsi_np.shape),
            "hsi_normalization": asdict(hsi_spec),
            "msi_normalization": asdict(msi_spec),
            "training_amp": amp,
            "inference_amp": args.amp_inference,
            "strict_fp32_inference": not args.amp_inference,
            "gpu": torch.cuda.get_device_name(device) if device.type == "cuda" else None,
        }
    )
    _save_json(output_dir / "run_config.json", config)

    print(f"Input:  {args.input}", flush=True)
    print(f"Output: {output_dir}", flush=True)
    print(f"Shapes: LRHSI={lrhsi_np.shape}, HRMSI={hrmsi_np.shape}, ratio={ratio}", flush=True)
    print(f"Normalization: HSI={hsi_spec}, MSI={msi_spec}", flush=True)
    if device.type == "cuda":
        print(
            f"GPU: {torch.cuda.get_device_name(device)}; "
            f"training AMP={amp}; inference AMP={args.amp_inference}",
            flush=True,
        )

    lrhsi = _to_nchw(lrhsi_np).to(device)
    hrmsi = _to_nchw(hrmsi_np).to(device)

    started = time.perf_counter()
    blind_model, blind_losses = train_blind(
        lrhsi,
        hrmsi,
        ratio,
        args.blind_iters,
        args.patch_hr,
        args.batch_size,
        args.blind_lr,
        device,
        rng,
        amp,
        args.log_every,
    )
    blind_seconds = time.perf_counter() - started
    with torch.no_grad():
        psf, srf = blind_model.kernels()
        spectral_gain, spectral_bias = blind_model.calibration()
        psf_cpu = psf.detach().float().cpu()
        srf_cpu = srf.detach().float().cpu()
        gain_cpu = spectral_gain.detach().float().cpu()
        bias_cpu = spectral_bias.detach().float().cpu()
    _save_torch(
        output_dir / "blind_model.pt",
        {
            "state_dict": blind_model.state_dict(),
            "ratio": ratio,
            "hs_bands": lrhsi.shape[1],
            "ms_bands": hrmsi.shape[1],
        },
    )
    sio.savemat(
        output_dir / "BR.mat",
        {
            "B": psf_cpu.squeeze().numpy(),
            "R": srf_cpu.squeeze(-1).squeeze(-1).numpy(),
            "G": gain_cpu.flatten().numpy(),
            "C": bias_cpu.flatten().numpy(),
        },
    )

    fusion_started = time.perf_counter()
    fusion_model, fusion_losses = train_fusion(
        lrhsi,
        hrmsi,
        ratio,
        psf_cpu,
        srf_cpu,
        gain_cpu,
        bias_cpu,
        args.edm_num,
        args.stages,
        args.fusion_iters,
        args.patch_hr,
        args.batch_size,
        args.fusion_lr,
        args.weight_decay,
        device,
        rng,
        amp,
        args.log_every,
    )
    fusion_seconds = time.perf_counter() - fusion_started
    _save_torch(
        output_dir / "miae_model.pt",
        {
            "state_dict": fusion_model.state_dict(),
            "hs_bands": lrhsi.shape[1],
            "ms_bands": hrmsi.shape[1],
            "edm_num": args.edm_num,
            "stages": args.stages,
        },
    )

    # Inference reads CPU tensors tile by tile so full-resolution activations never accumulate on the GPU.
    lrhsi_cpu = lrhsi.detach().cpu()
    hrmsi_cpu = hrmsi.detach().cpu()
    del lrhsi, hrmsi, blind_model
    if device.type == "cuda":
        torch.cuda.empty_cache()
    inference_started = time.perf_counter()
    x_npy = output_dir / "X_normalized.npy"
    z_npy = output_dir / "Z_from_SRF.npy"
    fused = infer_tiled(
        fusion_model,
        lrhsi_cpu,
        hrmsi_cpu,
        ratio,
        args.tile_size,
        args.halo_lr,
        device,
        amp=args.amp_inference,
        output_memmap_path=x_npy,
        srf=srf_cpu,
        spectral_gain=gain_cpu,
        spectral_bias=bias_cpu,
        synthesized_output_path=z_npy,
    )
    inference_seconds = time.perf_counter() - inference_started
    synthesized = np.load(z_npy, mmap_mode="r")

    preview = colorize_srf_msi(synthesized, hrmsi_np)
    reference_preview = colorize_srf_msi(hrmsi_np, hrmsi_np)
    _save_png(output_dir / "preview_srf_rgb.png", preview)
    _save_png(output_dir / "reference_hrmsi.png", reference_preview)
    _save_png(
        output_dir / "preview_comparison.png",
        np.concatenate((reference_preview, preview), axis=1),
    )
    sio.savemat(
        output_dir / "synthesized_msi.mat",
        {
            "Z_from_SRF": np.asarray(synthesized),
            "HRMSI_normalized": hrmsi_np,
            "R": srf_cpu.squeeze(-1).squeeze(-1).numpy(),
            "G": gain_cpu.flatten().numpy(),
            "C": bias_cpu.flatten().numpy(),
        },
        do_compression=False,
    )
    if not args.skip_mat:
        print("Saving X.mat (this may take several minutes for the full cube)...", flush=True)
        sio.savemat(
            output_dir / "X.mat",
            {
                "X": fused,
                "normalization_low": np.float64(hsi_spec.low),
                "normalization_high": np.float64(hsi_spec.high),
                "t_blind": np.float64(blind_seconds),
                "t_train": np.float64(fusion_seconds),
                "t_infer": np.float64(inference_seconds),
            },
            do_compression=False,
        )

    summary = {
        "blind_seconds": blind_seconds,
        "fusion_seconds": fusion_seconds,
        "inference_seconds": inference_seconds,
        "blind_final_loss": blind_losses[-1],
        "fusion_final_loss": fusion_losses[-1],
        "synthesized_msi_mae": float(
            np.mean(np.abs(np.asarray(synthesized) - hrmsi_np), dtype=np.float64)
        ),
        "blind_losses": blind_losses,
        "fusion_losses": fusion_losses,
    }
    _save_json(output_dir / "training_log.json", summary)
    print(f"Completed. Results saved to: {output_dir}", flush=True)


if __name__ == "__main__":
    main()
