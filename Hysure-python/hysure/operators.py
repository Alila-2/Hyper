"""Array layout, sampling, convolution, and proximal operators.

The conversion helpers deliberately reproduce MATLAB's column-major
``reshape`` semantics. HySure is sensitive to pixel ordering, so ordinary
NumPy C-order reshapes must not be substituted here.
"""

from __future__ import annotations

import numpy as np
from scipy import fft


def image_to_matrix(image: np.ndarray) -> np.ndarray:
    """Convert an H-by-W-by-B cube to a B-by-(H*W) MATLAB-order matrix."""
    if image.ndim != 3:
        raise ValueError("image_to_matrix expects an H-by-W-by-B array")
    height, width, bands = image.shape
    return np.ascontiguousarray(
        image.reshape((height * width, bands), order="F").T
    )


def matrix_to_image(matrix: np.ndarray, height: int) -> np.ndarray:
    """Convert a B-by-N matrix to an H-by-W-by-B MATLAB-order cube."""
    if matrix.ndim != 2:
        raise ValueError("matrix_to_image expects a two-dimensional matrix")
    bands, pixels = matrix.shape
    if pixels % height:
        raise ValueError("Matrix pixel count is not divisible by height")
    width = pixels // height
    return np.ascontiguousarray(
        matrix.T.reshape((height, width, bands), order="F")
    )


def upsample_hs(
    image: np.ndarray,
    factor: int,
    out_height: int,
    out_width: int,
    shift: int,
) -> np.ndarray:
    """Zero-insertion upsampling equivalent to MATLAB ``upsamp_HS``."""
    if factor < 1 or shift < 0 or shift >= factor:
        raise ValueError("Invalid upsampling factor or phase shift")
    result = np.zeros(
        (image.shape[0] * factor, image.shape[1] * factor, image.shape[2]),
        dtype=image.dtype,
    )
    result[shift::factor, shift::factor, :] = image
    return np.ascontiguousarray(result[:out_height, :out_width, :])


def downsample_hs(image: np.ndarray, factor: int, shift: int) -> np.ndarray:
    """Phase-aware spatial downsampling equivalent to ``downsamp_HS``."""
    if factor < 1 or shift < 0 or shift >= factor:
        raise ValueError("Invalid downsampling factor or phase shift")
    return np.ascontiguousarray(image[shift::factor, shift::factor, :])


def conv_c(matrix: np.ndarray, filter_fft: np.ndarray, height: int) -> np.ndarray:
    """Apply the same circular 2-D convolution to every matrix row."""
    cube = matrix_to_image(matrix, height)
    transformed = fft.fft2(cube, axes=(0, 1), workers=-1)
    transformed *= filter_fft[:, :, None]
    convolved = fft.ifft2(transformed, axes=(0, 1), workers=-1).real
    return image_to_matrix(convolved)


def vector_soft_col_iso(
    x_horizontal: np.ndarray,
    x_vertical: np.ndarray,
    tau: float,
) -> tuple[np.ndarray, np.ndarray]:
    """Columnwise isotropic vector soft thresholding."""
    magnitude = np.sqrt(
        np.sum(x_horizontal * x_horizontal, axis=0)
        + np.sum(x_vertical * x_vertical, axis=0)
    )
    scale = np.maximum(0.0, magnitude - tau) / (magnitude + 1e-300)
    return x_horizontal * scale[None, :], x_vertical * scale[None, :]

