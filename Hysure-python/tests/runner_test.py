"""End-to-end smoke test for the backend-facing HySure runner."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import h5py
from scipy.io import savemat


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "hysure_runner.py"


def _run(*arguments: str) -> dict:
    completed = subprocess.run(
        [sys.executable, str(RUNNER), *arguments],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
    )
    result_path = Path(arguments[arguments.index("--result-json") + 1])
    if completed.returncode != 0:
        detail = completed.stdout + completed.stderr
        if result_path.is_file():
            detail += result_path.read_text(encoding="utf-8")
        raise AssertionError(detail)
    result = json.loads(result_path.read_text(encoding="utf-8"))
    assert result["status"] == "success", completed.stdout + completed.stderr
    return result


def main() -> None:
    rng = np.random.default_rng(23)
    low = rng.uniform(0.05, 0.95, size=(16, 16, 80)).astype(np.float32)
    response = np.zeros((3, 80), dtype=np.float64)
    response[0, 44:76] = 1.0 / 32.0
    response[1, 23:54] = 1.0 / 31.0
    response[2, 7:35] = 1.0 / 28.0
    low_rgb = np.einsum("hwb,cb->hwc", low, response)
    high = np.repeat(np.repeat(low_rgb, 2, axis=0), 2, axis=1)
    high += rng.normal(scale=1e-3, size=high.shape)
    high = np.clip(high, 0.0, 1.0).astype(np.float32)

    with tempfile.TemporaryDirectory(prefix="hysure_runner_test_") as temporary:
        work = Path(temporary)
        input_file = work / "input.mat"
        output_dir = work / "output"
        fusion_json = work / "fusion.json"
        savemat(input_file, {"LRHSI": low, "HRMSI": high})

        fused = _run(
            "fuse",
            "--input",
            str(input_file),
            "--output-dir",
            str(output_dir),
            "--subspace",
            "4",
            "--iterations",
            "2",
            "--blur-support",
            "6",
            "--block-rows",
            "8",
            "--result-json",
            str(fusion_json),
        )
        assert (fused["height"], fused["width"], fused["bands"]) == (32, 32, 80)
        fusion_file = Path(fused["fusion_file"])
        assert fusion_file.is_file()
        assert fusion_file.name == "dataset_final_hysure.mat"
        assert not list(output_dir.glob("*.h5"))
        assert Path(fused["preview_file"]).is_file()
        assert fusion_file.read_bytes()[:19] == b"MATLAB 7.3 MAT-file"
        with h5py.File(fusion_file, "r") as handle:
            assert handle["FusedHSI"].shape == (80, 32, 32)
            assert handle["FusedHSI"].attrs["MATLAB_class"] == b"single"
            assert handle["R_est"].attrs["MATLAB_class"] == b"double"
            assert handle["scaleInfo"].attrs["MATLAB_class"] == b"struct"

        info_json = work / "info.json"
        info = _run(
            "info",
            "--fusion-file",
            fused["fusion_file"],
            "--result-json",
            str(info_json),
        )
        assert info["dtype"] == "float32"

        spectrum_json = work / "spectrum.json"
        spectrum = _run(
            "spectrum",
            "--fusion-file",
            fused["fusion_file"],
            "--x",
            "5",
            "--y",
            "7",
            "--result-json",
            str(spectrum_json),
        )
        assert len(spectrum["spectrum"]) == 80
        assert np.all(np.isfinite(spectrum["spectrum"]))
    print("HySure backend runner test passed")


if __name__ == "__main__":
    main()
