"""Vertex Component Analysis used by the original HySure implementation."""

from __future__ import annotations

import numpy as np
from scipy import linalg


def _largest_eigenvectors(matrix: np.ndarray, count: int) -> np.ndarray:
    values, vectors = linalg.eigh(
        matrix,
        subset_by_index=[matrix.shape[0] - count, matrix.shape[0] - 1],
        check_finite=False,
    )
    order = np.argsort(values)[::-1]
    return vectors[:, order]


def vca(
    spectra: np.ndarray,
    endmembers: int,
    rng: np.random.Generator,
    snr: float | None = None,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Estimate endmembers from an L-by-N spectral matrix.

    Returns ``(endmember_matrix, selected_indices, projected_spectra)``.
    """
    if spectra.ndim != 2 or spectra.size == 0:
        raise ValueError("VCA spectra must be a non-empty L-by-N matrix")
    bands, pixels = spectra.shape
    if not 1 <= endmembers <= bands:
        raise ValueError("endmembers must be between 1 and the band count")

    if bands - endmembers < endmembers and snr is None:
        snr = 100.0

    mean_spectrum = np.mean(spectra, axis=1, keepdims=True)
    correlation = spectra @ spectra.T / pixels
    basis = _largest_eigenvectors(correlation, endmembers)

    if snr is None:
        projected_power = np.sum(
            np.einsum("ij,ji->i", basis.T @ correlation, basis)
        )
        total_power = np.trace(correlation)
        noise_power = (total_power - projected_power) / (
            1.0 - endmembers / bands
        )
        snr = (
            10.0 * np.log10(projected_power / noise_power)
            if noise_power > 0
            else 0.0
        )

    threshold = 15.0 + 10.0 * np.log10(endmembers)
    if snr < threshold:
        dimension = endmembers - 1
        covariance = correlation - mean_spectrum @ mean_spectrum.T
        basis = _largest_eigenvectors(covariance, dimension)
        centered = spectra - mean_spectrum
        projected_coordinates = basis.T @ centered
        projected_spectra = basis @ projected_coordinates + mean_spectrum
        lift = np.sqrt(np.max(np.sum(projected_coordinates**2, axis=0)))
        y = np.vstack(
            [projected_coordinates, np.full((1, pixels), lift)]
        )
    else:
        dimension = endmembers
        projected_coordinates = basis.T @ spectra
        projected_spectra = basis @ projected_coordinates
        direction = np.mean(projected_coordinates, axis=1) * endmembers
        scale = np.sum(projected_coordinates * direction[:, None], axis=0)
        bad = scale < 0.01
        safe_scale = scale.copy()
        safe_scale[bad] = 1.0
        y = projected_coordinates / safe_scale[None, :]
        if np.any(bad):
            y[:, bad] = (direction / np.linalg.norm(direction) ** 2)[:, None]

    dimension = y.shape[0]
    selected = np.zeros(dimension, dtype=np.int64)
    accumulator = np.zeros((dimension, dimension), dtype=np.float64)
    accumulator[-1, 0] = 1.0
    for index in range(dimension):
        random_direction = rng.random(dimension)
        direction = random_direction - accumulator @ np.linalg.pinv(
            accumulator
        ) @ random_direction
        direction /= np.linalg.norm(direction)
        projection = direction @ y
        selected[index] = int(np.argmax(np.abs(projection)))
        accumulator[:, index] = y[:, selected[index]]

    return projected_spectra[:, selected], selected, projected_spectra

