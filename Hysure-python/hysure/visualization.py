"""Natural-colour visualization of a Python HySure result."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import h5py
import imageio.v3 as iio
import numpy as np

from .matio import (
    load_variables,
    matlab_shape,
    read_cube_rows,
    read_matlab_array,
    read_scale_info,
    write_result_metadata,
)


@dataclass
class VisualizationOptions:
    fusion_file: Path
    reference_file: Path
    output_file: Path
    block_rows: int = 64
    sample_step: int = 16


def _normalize(cube: np.ndarray, low: float, high: float) -> np.ndarray:
    return np.clip((cube.astype(np.float64) - low) / (high - low), 0.0, 1.0)


def _project(cube: np.ndarray, response: np.ndarray) -> np.ndarray:
    return np.einsum("hwb,cb->hwc", cube, response, optimize=True)


def _write_rgb(path: Path, rgb: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    iio.imwrite(path, np.round(np.clip(rgb, 0.0, 1.0) * 255.0).astype(np.uint8))


def visualize_fusion(options: VisualizationOptions, progress=print) -> dict:
    reference = load_variables(options.reference_file, ("HRMSI",))["HRMSI"].astype(
        np.float32
    )
    if np.max(reference) > 1.5:
        reference /= 255.0
    reference = np.clip(reference, 0.0, 1.0)

    with h5py.File(options.fusion_file, "r") as handle:
        fused = handle["FusedHSI"]
        if fused.attrs.get("storage_order", b"") == b"python":
            response = np.asarray(handle["R_est"], dtype=np.float64)
        else:
            response = read_matlab_array(handle["R_est"]).astype(np.float64)
        scale = read_scale_info(handle["scaleInfo"])
        hsi_low = float(scale["HSILowPercentile"])
        hsi_high = float(scale["HSIHighPercentile"])
        height, width, bands = matlab_shape(fused)
        if response.shape != (3, bands):
            raise ValueError(f"R_est must have shape (3, {bands})")
        if reference.shape != (height, width, 3):
            raise ValueError("HRMSI spatial size does not match FusedHSI")

        sample_rows = np.arange(0, height, options.sample_step)
        sample_cols = np.arange(0, width, options.sample_step)
        sampled_rows = read_cube_rows(fused, sample_rows)
        sample_cube = sampled_rows[:, sample_cols, :]
        projected_samples = _project(
            _normalize(sample_cube, hsi_low, hsi_high), response
        )
        reference_samples = reference[np.ix_(sample_rows, sample_cols)]
        design = np.column_stack(
            [projected_samples.reshape(-1, 3), np.ones(projected_samples.shape[0] * projected_samples.shape[1])]
        )
        target = reference_samples.reshape(-1, 3)
        valid = np.all(np.isfinite(design), axis=1) & np.all(
            np.isfinite(target), axis=1
        )
        if np.count_nonzero(valid) < 100:
            raise RuntimeError("Not enough valid samples for colour correction")
        colour_transform, *_ = np.linalg.lstsq(
            design[valid], target[valid], rcond=None
        )

        raw_rgb = np.empty((height, width, 3), dtype=np.float32)
        corrected_rgb = np.empty_like(raw_rgb)
        for first in range(0, height, options.block_rows):
            last = min(height, first + options.block_rows)
            cube = _normalize(read_cube_rows(fused, slice(first, last)), hsi_low, hsi_high)
            projected = _project(cube, response)
            raw_rgb[first:last] = np.clip(projected, 0.0, 1.0)
            pixels = np.column_stack(
                [projected.reshape(-1, 3), np.ones(projected.shape[0] * width)]
            )
            corrected = (pixels @ colour_transform).reshape(last - first, width, 3)
            corrected_rgb[first:last] = np.clip(corrected, 0.0, 1.0)
            progress(f"Visualized rows {first + 1}-{last}/{height}")

    raw_file = options.output_file.with_name(
        options.output_file.stem + "_R_est_uncorrected.png"
    )
    _write_rgb(raw_file, raw_rgb)
    _write_rgb(options.output_file, corrected_rgb)
    settings_file = options.output_file.with_name(
        options.output_file.stem + "_settings.npz"
    )
    np.savez(
        settings_file,
        R=response,
        colour_transform=colour_transform,
        hsi_low=hsi_low,
        hsi_high=hsi_high,
    )
    result = {
        "status": "success",
        "output_file": str(options.output_file.resolve()),
        "raw_file": str(raw_file.resolve()),
        "settings_file": str(settings_file.resolve()),
    }
    write_result_metadata(
        options.output_file.with_name(options.output_file.stem + "_result.json"),
        result,
    )
    return result
