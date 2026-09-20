"""End-to-end whole-image HySure fusion workflow."""

from __future__ import annotations

import time
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np

from .data_fusion import data_fusion
from .matio import FusionResultWriter, load_variables, write_result_metadata
from .operators import matrix_to_image
from .sensor_response import estimate_sensor_response


@dataclass
class FusionOptions:
    input_file: Path
    output_dir: Path
    subspace_dimension: int = 10
    random_seed: int = 0
    lambda_r: float = 1.0
    lambda_b: float = 1.0
    lambda_phi: float = 5e-4
    lambda_m: float = 1.0
    blur_support: int = 10
    admm_mu: float = 0.001
    admm_iterations: int = 30
    output_block_rows: int = 32
    dry_run: bool = False


def _validate_inputs(low_hsi: np.ndarray, high_msi: np.ndarray) -> int:
    if low_hsi.ndim != 3 or not np.issubdtype(low_hsi.dtype, np.number):
        raise ValueError("LRHSI must be a numeric H-by-W-by-B cube")
    if high_msi.ndim != 3 or high_msi.shape[2] != 3:
        raise ValueError("HRMSI must be an H-by-W-by-3 RGB image")
    if not np.all(np.isfinite(low_hsi)) or not np.all(np.isfinite(high_msi)):
        raise ValueError("Input arrays contain NaN or Inf")
    scale_y = high_msi.shape[0] / low_hsi.shape[0]
    scale_x = high_msi.shape[1] / low_hsi.shape[1]
    if scale_x != scale_y or scale_x != round(scale_x):
        raise ValueError("HRMSI/LRHSI spatial ratio must be the same integer")
    return int(round(scale_x))


def run_fusion(options: FusionOptions, progress=print) -> dict:
    """Fuse the complete LRHSI and HRMSI and write a chunked HDF5 result."""
    started = time.perf_counter()
    options.output_dir.mkdir(parents=True, exist_ok=True)
    progress(f"Loading {options.input_file}")
    inputs = load_variables(options.input_file, ("LRHSI", "HRMSI"))
    low_hsi = inputs["LRHSI"]
    high_msi = inputs["HRMSI"]
    downsample_factor = _validate_inputs(low_hsi, high_msi)
    progress(
        f"LRHSI={low_hsi.shape}, HRMSI={high_msi.shape}, "
        f"scale={downsample_factor}x"
    )

    hsi_low = float(np.percentile(low_hsi, 0.1))
    hsi_high = float(np.percentile(low_hsi, 99.9))
    if not hsi_high > hsi_low:
        raise ValueError("LRHSI has no usable dynamic range")
    normalized_hsi = np.clip(
        (low_hsi.astype(np.float64) - hsi_low) / (hsi_high - hsi_low),
        0.0,
        1.0,
    )
    del low_hsi

    normalized_msi = high_msi.astype(np.float64)
    if not np.max(normalized_msi) > np.min(normalized_msi):
        raise ValueError("HRMSI has no usable dynamic range")
    msi_divisor = 255.0 if np.max(normalized_msi) > 1.5 else 1.0
    normalized_msi = np.clip(normalized_msi / msi_divisor, 0.0, 1.0)
    del high_msi

    scale_info = {
        "HSILowPercentile": hsi_low,
        "HSIHighPercentile": hsi_high,
        "MSIDivisor": msi_divisor,
        "DownsampleFactor": downsample_factor,
    }
    if options.dry_run:
        result = {
            "status": "dry-run-ok",
            "input_file": str(options.input_file.resolve()),
            "lrhsi_shape": list(normalized_hsi.shape),
            "hrmsi_shape": list(normalized_msi.shape),
            "scale_info": scale_info,
        }
        write_result_metadata(options.output_dir / "dry_run.json", result)
        return result

    band_count = normalized_hsi.shape[2]
    # MATLAB 45:76, 24:54 and 8:35 converted to zero-based Python slices.
    intersections = [
        np.arange(44, 76, dtype=np.int64),
        np.arange(23, 54, dtype=np.int64),
        np.arange(7, 35, dtype=np.int64),
    ]
    if band_count <= max(int(np.max(item)) for item in intersections):
        raise ValueError("LRHSI does not contain the expected visible bands")
    subspace_dimension = min(
        options.subspace_dimension,
        band_count,
        normalized_hsi.shape[0] * normalized_hsi.shape[1] - 1,
    )

    progress("Estimating spectral response, blur, and denoising basis")
    denoising_basis, response, blur = estimate_sensor_response(
        normalized_hsi,
        normalized_msi,
        downsample_factor,
        intersections,
        intersections,
        subspace_dimension,
        options.lambda_r,
        options.lambda_b,
        options.blur_support,
        options.blur_support,
        1,
        0,
    )

    progress("Running HySure ADMM fusion")
    rng = np.random.default_rng(options.random_seed)
    state = data_fusion(
        normalized_hsi,
        normalized_msi,
        downsample_factor,
        response,
        blur,
        subspace_dimension,
        options.lambda_phi,
        options.lambda_m,
        rng,
        options.admm_mu,
        options.admm_iterations,
        progress,
    )
    del normalized_hsi, normalized_msi

    # Original postprocessing: V * V' * (E * X). Combine the bases first.
    reconstruction_basis = denoising_basis @ (
        denoising_basis.T @ state.basis
    )
    coefficient_image = matrix_to_image(state.coefficients, state.height)
    output_file = options.output_dir / "dataset_final_hysure.mat"
    progress(f"Writing {output_file} in row blocks")
    with FusionResultWriter(
        output_file,
        (state.height, state.width, band_count),
        response,
        blur,
        scale_info,
        options.output_block_rows,
    ) as writer:
        for first in range(0, state.height, options.output_block_rows):
            last = min(state.height, first + options.output_block_rows)
            normalized_block = np.einsum(
                "bp,hwp->hwb",
                reconstruction_basis,
                coefficient_image[first:last, :, :],
                optimize=True,
            )
            restored = (
                np.clip(normalized_block, 0.0, 1.0)
                * (hsi_high - hsi_low)
                + hsi_low
            )
            writer.write_rows(first, last, restored)
            progress(f"Saved rows {first + 1}-{last}/{state.height}")

    result = {
        "status": "success",
        "input_file": str(options.input_file.resolve()),
        "output_file": str(output_file.resolve()),
        "fused_shape": [state.height, state.width, band_count],
        "elapsed_seconds": time.perf_counter() - started,
        "scale_info": scale_info,
        "options": {
            key: str(value) if isinstance(value, Path) else value
            for key, value in asdict(options).items()
        },
    }
    write_result_metadata(options.output_dir / "fusion_result.json", result)
    return result
