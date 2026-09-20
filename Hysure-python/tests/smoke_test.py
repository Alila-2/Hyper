"""Small numerical smoke test; it does not use the 360 MB production data."""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from hysure.data_fusion import data_fusion
from hysure.operators import (
    downsample_hs,
    image_to_matrix,
    matrix_to_image,
    upsample_hs,
)
from hysure.sensor_response import estimate_sensor_response


def main() -> None:
    rng = np.random.default_rng(7)

    cube = rng.normal(size=(5, 7, 4))
    restored = matrix_to_image(image_to_matrix(cube), cube.shape[0])
    np.testing.assert_allclose(restored, cube)

    low = rng.uniform(0.05, 0.95, size=(16, 16, 40))
    response_true = np.zeros((3, 40))
    response_true[0, 0:12] = 1.0 / 12
    response_true[1, 12:26] = 1.0 / 14
    response_true[2, 26:40] = 1.0 / 14
    low_rgb = np.einsum("hwb,cb->hwc", low, response_true)
    high_rgb = np.repeat(np.repeat(low_rgb, 2, axis=0), 2, axis=1)
    high_rgb += rng.normal(scale=1e-3, size=high_rgb.shape)
    high_rgb = np.clip(high_rgb, 0.0, 1.0)

    inserted = upsample_hs(low[:, :, :3], 2, 32, 32, 1)
    sampled = downsample_hs(inserted, 2, 1)
    np.testing.assert_allclose(sampled, low[:, :, :3])

    intersections = [
        np.arange(0, 12),
        np.arange(12, 26),
        np.arange(26, 40),
    ]
    denoising_basis, response, blur = estimate_sensor_response(
        low,
        high_rgb,
        2,
        intersections,
        intersections,
        4,
        blur_height=6,
        blur_width=6,
    )
    assert denoising_basis.shape == (40, 4)
    assert response.shape == (3, 40)
    assert blur.shape == (32, 32)
    assert np.all(np.isfinite(response))
    assert np.all(np.isfinite(blur))
    np.testing.assert_allclose(np.sum(blur), 1.0, atol=1e-10)

    state = data_fusion(
        low,
        high_rgb,
        2,
        response,
        blur,
        4,
        5e-4,
        1.0,
        np.random.default_rng(11),
        iterations=2,
        progress=lambda _message: None,
    )
    assert state.basis.shape == (40, 4)
    assert state.coefficients.shape == (4, 32 * 32)
    assert np.all(np.isfinite(state.coefficients))
    print("HySure Python smoke test passed")


if __name__ == "__main__":
    main()

