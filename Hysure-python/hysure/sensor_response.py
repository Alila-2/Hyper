"""Relative spectral response and spatial blur estimation."""

from __future__ import annotations

import math

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view
from scipy import fft, linalg

from .operators import (
    conv_c,
    downsample_hs,
    image_to_matrix,
    matrix_to_image,
    upsample_hs,
)


def _matlab_round_positive(value: float) -> int:
    return int(math.floor(value + 0.5))


def _average_filter_fft(height: int, width: int, radius_y: int, radius_x: int):
    kernel = np.zeros((height, width), dtype=np.float64)
    middle_y = _matlab_round_positive((height + 1) / 2)
    middle_x = _matlab_round_positive((width + 1) / 2)
    start_y = middle_y - radius_y - 1
    start_x = middle_x - radius_x - 1
    kernel[
        start_y : start_y + 2 * radius_y + 1,
        start_x : start_x + 2 * radius_x + 1,
    ] = 1.0 / ((2 * radius_y + 1) * (2 * radius_x + 1))
    return fft.fft2(fft.ifftshift(kernel), workers=-1)


def _spectral_basis(yh: np.ndarray, dimension: int) -> np.ndarray:
    covariance = yh @ yh.T
    _, vectors = linalg.eigh(
        covariance,
        subset_by_index=[yh.shape[0] - dimension, yh.shape[0] - 1],
        check_finite=False,
    )
    return np.ascontiguousarray(vectors[:, ::-1])


def estimate_sensor_response(
    low_hsi: np.ndarray,
    high_msi: np.ndarray,
    downsample_factor: int,
    intersections: list[np.ndarray],
    contiguous: list[np.ndarray],
    subspace_dimension: int,
    lambda_r: float = 1.0,
    lambda_b: float = 1.0,
    blur_height: int = 10,
    blur_width: int = 10,
    shift: int = 1,
    blur_center: int = 0,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Python equivalent of ``sen_resp_est.m``.

    The implementation avoids materializing the 320-band high-resolution
    zero-filled cube. Projection and upsampling commute, so only the small
    subspace coefficient cube is upsampled; the numerical model is unchanged.
    """
    high_height, high_width, msi_bands = high_msi.shape
    low_height, low_width, hsi_bands = low_hsi.shape

    high_filter = _average_filter_fft(high_height, high_width, 4, 4)
    high_blurred = conv_c(image_to_matrix(high_msi), high_filter, high_height)
    high_blurred_image = matrix_to_image(high_blurred, high_height)
    high_blurred_down = image_to_matrix(
        downsample_hs(high_blurred_image, downsample_factor, shift)
    )

    low_radius = _matlab_round_positive(4 / downsample_factor)
    low_filter = _average_filter_fft(
        low_height, low_width, low_radius, low_radius
    )
    yh = image_to_matrix(low_hsi)
    low_blurred = conv_c(yh, low_filter, low_height)

    response = np.zeros((msi_bands, hsi_bands), dtype=np.float64)
    for channel, indices in enumerate(intersections):
        count = indices.size
        difference = np.zeros((count - 1, count), dtype=np.float64)
        rows = np.arange(count - 1)
        difference[rows, rows] = 1.0
        difference[rows, rows + 1] = -1.0
        gaps = np.diff(contiguous[channel]) != 1
        difference[gaps, :] = 0.0
        selected = low_blurred[indices, :]
        system = selected @ selected.T + lambda_r * (
            difference.T @ difference
        )
        target = selected @ high_blurred_down[channel, :]
        response[channel, indices] = linalg.solve(
            system, target, assume_a="sym"
        )

    basis = _spectral_basis(yh, subspace_dimension)
    low_coefficients = basis.T @ yh
    low_coefficient_image = matrix_to_image(low_coefficients, low_height)
    high_coefficient_image = upsample_hs(
        low_coefficient_image,
        downsample_factor,
        high_height,
        high_width,
        shift,
    )
    high_coefficients = image_to_matrix(high_coefficient_image)
    projected_response = response @ basis @ high_coefficients
    projected_image = matrix_to_image(projected_response, high_height)

    difference_v = np.zeros((blur_height - 1, blur_height), dtype=np.float64)
    rows = np.arange(blur_height - 1)
    difference_v[rows, rows] = 1.0
    difference_v[rows, rows + 1] = -1.0
    difference_h = difference_v.T
    regularizer_h = np.kron(difference_h.T, np.eye(blur_width))
    regularizer_v = np.kron(np.eye(blur_height), difference_v)
    regularizer = regularizer_h.T @ regularizer_h + regularizer_v.T @ regularizer_v

    patch_count = blur_height * blur_width
    patch_covariance = np.zeros((patch_count, patch_count), dtype=np.float64)
    cross_covariance = np.zeros(patch_count, dtype=np.float64)

    half_y = (blur_height - 1) // 2
    half_x = (blur_width - 1) // 2
    first_center_y = half_y
    first_center_x = half_x
    if first_center_y % downsample_factor != shift:
        first_center_y += (shift - first_center_y) % downsample_factor
    if first_center_x % downsample_factor != shift:
        first_center_x += (shift - first_center_x) % downsample_factor
    last_center_y = high_height - half_y - 2
    last_center_x = high_width - half_x - 2
    centers_y = np.arange(
        first_center_y, last_center_y + 1, downsample_factor
    )
    centers_x = np.arange(
        first_center_x, last_center_x + 1, downsample_factor
    )
    starts_y = centers_y - half_y
    starts_x = centers_x - half_x

    for channel in range(msi_bands):
        windows = sliding_window_view(
            high_msi[:, :, channel], (blur_height, blur_width)
        )
        patches = windows[np.ix_(starts_y, starts_x)]
        # MATLAB patch(:) is column-major; swap the last axes before C-flatten.
        patches = np.ascontiguousarray(
            patches.swapaxes(-1, -2).reshape(-1, patch_count)
        )
        targets = projected_image[
            np.ix_(centers_y, centers_x, np.array([channel]))
        ].reshape(-1)
        patch_covariance += patches.T @ patches
        cross_covariance += patches.T @ targets
        del windows, patches, targets

    blur_vector = linalg.solve(
        patch_covariance + lambda_b * regularizer,
        cross_covariance,
        assume_a="sym",
    )
    blur_small = blur_vector.reshape((blur_height, blur_width), order="F")
    blur = np.zeros((high_height, high_width), dtype=np.float64)
    middle_y = _matlab_round_positive((high_height + 1) / 2)
    middle_x = _matlab_round_positive((high_width + 1) / 2)
    start_y = middle_y - ((blur_height - 1) // 2) - blur_center - 1
    start_x = middle_x - ((blur_width - 1) // 2) - blur_center - 1
    blur[start_y : start_y + blur_height, start_x : start_x + blur_width] = blur_small
    blur = fft.ifftshift(blur)
    volume = np.sum(blur)
    if not np.isfinite(volume) or abs(volume) < 1e-15:
        raise RuntimeError("Estimated spatial response has zero DC gain")
    blur /= volume
    response /= volume
    return basis, response, blur

