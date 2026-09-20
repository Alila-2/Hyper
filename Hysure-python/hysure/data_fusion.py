"""HySure ADMM/SALSA fusion solver."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy import fft, linalg

from .operators import (
    conv_c,
    image_to_matrix,
    matrix_to_image,
    upsample_hs,
    vector_soft_col_iso,
)
from .vca import vca


@dataclass
class FusionCoefficients:
    basis: np.ndarray
    coefficients: np.ndarray
    height: int
    width: int


def data_fusion(
    low_hsi: np.ndarray,
    high_msi: np.ndarray,
    downsample_factor: int,
    spectral_response: np.ndarray,
    spatial_response: np.ndarray,
    subspace_dimension: int,
    lambda_phi: float,
    lambda_m: float,
    rng: np.random.Generator,
    mu: float = 0.001,
    iterations: int = 30,
    progress=print,
) -> FusionCoefficients:
    """Run the original HySure optimization and return its compact state."""
    height, width, _ = high_msi.shape
    low_height = low_hsi.shape[0]

    difference_h = np.zeros((height, width), dtype=np.float64)
    difference_h[0, 0] = 1.0
    difference_h[0, -1] = -1.0
    difference_v = np.zeros((height, width), dtype=np.float64)
    difference_v[0, 0] = 1.0
    difference_v[-1, 0] = -1.0

    fdh = fft.fft2(difference_h, workers=-1)
    fdv = fft.fft2(difference_v, workers=-1)
    fb = fft.fft2(spatial_response, workers=-1)
    denominator = np.abs(fb) ** 2 + np.abs(fdh) ** 2 + np.abs(fdv) ** 2 + 1.0
    ibd_b = np.conj(fb) / denominator
    ibd_identity = 1.0 / denominator
    ibd_dh = np.conj(fdh) / denominator
    ibd_dv = np.conj(fdv) / denominator

    shift = 1
    mask_image = np.zeros((height, width), dtype=np.float64)
    mask_image[shift::downsample_factor, shift::downsample_factor] = 1.0
    mask = mask_image.reshape(1, -1, order="F")
    yh = image_to_matrix(low_hsi)

    best_volume = -np.inf
    basis = None
    # The non-zero samples of MATLAB's zero-filled Yh_up are exactly Yh.
    for _ in range(20):
        candidate, _, _ = vca(yh, subspace_dimension, rng, snr=0.0)
        sign, logdet = np.linalg.slogdet(candidate.T @ candidate)
        volume = sign * np.exp(logdet) if sign > 0 else 0.0
        if volume > best_volume:
            best_volume = volume
            basis = candidate
    if basis is None:
        raise RuntimeError("VCA failed to produce a spectral basis")

    low_projected = basis.T @ yh
    projected_image = matrix_to_image(low_projected, low_height)
    yyh = image_to_matrix(
        upsample_hs(
            projected_image,
            downsample_factor,
            height,
            width,
            shift,
        )
    )

    identity_system = basis.T @ basis + mu * np.eye(subspace_dimension)
    spectral_system = (
        lambda_m
        * basis.T
        @ (spectral_response.T @ spectral_response)
        @ basis
        + mu * np.eye(subspace_dimension)
    )
    ym = image_to_matrix(high_msi)
    yym = basis.T @ spectral_response.T @ ym
    del ym

    shape = (subspace_dimension, height * width)
    x = np.zeros(shape, dtype=np.float64)
    v1 = np.zeros_like(x)
    d1 = np.zeros_like(x)
    v2 = np.zeros_like(x)
    d2 = np.zeros_like(x)
    v3 = np.zeros_like(x)
    d3 = np.zeros_like(x)
    v4 = np.zeros_like(x)
    d4 = np.zeros_like(x)

    for iteration in range(1, iterations + 1):
        x = conv_c(v1 + d1, ibd_b, height)
        x += conv_c(v2 + d2, ibd_identity, height)
        x += conv_c(v3 + d3, ibd_dh, height)
        x += conv_c(v4 + d4, ibd_dv, height)

        nu1 = conv_c(x, fb, height) - d1
        solved_v1 = linalg.solve(
            identity_system,
            yyh + mu * nu1,
            assume_a="pos",
            check_finite=False,
        )
        v1 = solved_v1 * mask + nu1 * (1.0 - mask)

        nu2 = x - d2
        v2 = linalg.solve(
            spectral_system,
            lambda_m * yym + mu * nu2,
            assume_a="pos",
            check_finite=False,
        )

        nu3 = conv_c(x, fdh, height) - d3
        nu4 = conv_c(x, fdv, height) - d4
        v3, v4 = vector_soft_col_iso(nu3, nu4, lambda_phi / mu)

        d1 = -nu1 + v1
        d2 = -nu2 + v2
        d3 = -nu3 + v3
        d4 = -nu4 + v4
        progress(f"ADMM iteration {iteration}/{iterations}")

    return FusionCoefficients(basis, x, height, width)

