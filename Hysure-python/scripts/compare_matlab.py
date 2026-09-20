"""Compare a MATLAB v7.3 HySure result with the Python HDF5 result."""

from __future__ import annotations

import argparse

import h5py
import numpy as np


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("matlab_result")
    parser.add_argument("python_result")
    parser.add_argument("--block-rows", type=int, default=32)
    args = parser.parse_args()

    with h5py.File(args.matlab_result, "r") as matlab_file, h5py.File(
        args.python_result, "r"
    ) as python_file:
        matlab_cube = matlab_file["FusedHSI"]  # MATLAB HDF5: B,W,H
        python_cube = python_file["FusedHSI"]  # Python HDF5: H,W,B
        expected_shape = (
            matlab_cube.shape[2],
            matlab_cube.shape[1],
            matlab_cube.shape[0],
        )
        if python_cube.shape != expected_shape:
            raise ValueError(
                f"Shape mismatch: MATLAB {expected_shape}, Python {python_cube.shape}"
            )

        squared_error = 0.0
        absolute_error = 0.0
        reference_energy = 0.0
        count = 0
        for first in range(0, python_cube.shape[0], args.block_rows):
            last = min(python_cube.shape[0], first + args.block_rows)
            matlab_block = np.asarray(matlab_cube[:, :, first:last]).transpose(2, 1, 0)
            python_block = np.asarray(python_cube[first:last, :, :])
            difference = python_block.astype(np.float64) - matlab_block.astype(np.float64)
            squared_error += float(np.sum(difference**2))
            absolute_error += float(np.sum(np.abs(difference)))
            reference_energy += float(np.sum(matlab_block.astype(np.float64) ** 2))
            count += difference.size

        matlab_r = np.asarray(matlab_file["R_est"]).T
        python_r = np.asarray(python_file["R_est"])
        matlab_b = np.asarray(matlab_file["B_est"]).T
        python_b = np.asarray(python_file["B_est"])
        print(f"Cube RMSE: {np.sqrt(squared_error / count):.9g}")
        print(f"Cube MAE:  {absolute_error / count:.9g}")
        print(
            "Cube relative RMSE: "
            f"{np.sqrt(squared_error / max(reference_energy, 1e-300)):.9g}"
        )
        print(f"R_est RMSE: {np.sqrt(np.mean((python_r - matlab_r) ** 2)):.9g}")
        print(f"B_est RMSE: {np.sqrt(np.mean((python_b - matlab_b) ** 2)):.9g}")


if __name__ == "__main__":
    main()

