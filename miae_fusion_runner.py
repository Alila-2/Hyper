"""Machine-readable bridge between Spring Boot and train_fuse_final.py."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

import numpy as np


def _write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def _training_script() -> Path:
    script = Path(__file__).resolve().parent / "train_fuse_final.py"
    if not script.is_file():
        raise FileNotFoundError(f"MIAE training script not found: {script}")
    return script


def _run_fuse(args: argparse.Namespace) -> dict:
    output_dir = args.output_dir.resolve()
    run_name = "result"
    run_dir = output_dir / run_name
    command = [
        sys.executable,
        str(_training_script()),
        "--input",
        str(args.input.resolve()),
        "--output-root",
        str(output_dir),
        "--run-name",
        run_name,
        "--device",
        args.device,
        "--seed",
        str(args.seed),
        "--blind-iters",
        str(args.blind_iters),
        "--fusion-iters",
        str(args.fusion_iters),
        "--batch-size",
        str(args.batch_size),
        "--patch-hr",
        str(args.patch_size),
        "--edm-num",
        str(args.endmembers),
        "--stages",
        str(args.stages),
        "--tile-size",
        str(args.tile_size),
        "--halo-lr",
        str(args.halo_lr),
        "--log-every",
        str(args.log_every),
    ]
    if args.amp_inference:
        command.append("--amp-inference")
    if args.smoke_test:
        command.append("--smoke-test")

    started = time.perf_counter()
    completed = subprocess.run(command, check=False)
    if completed.returncode != 0:
        raise RuntimeError(f"MIAE training exited with code {completed.returncode}")

    fusion_file = run_dir / "X_normalized.npy"
    mat_file = run_dir / "X.mat"
    preview_file = run_dir / "preview_srf_rgb.png"
    for required in (fusion_file, mat_file, preview_file):
        if not required.is_file():
            raise FileNotFoundError(f"MIAE output is missing: {required}")
    fused = np.load(fusion_file, mmap_mode="r")
    if fused.ndim != 3:
        raise ValueError("MIAE fused cube must be an H-by-W-by-B array")
    height, width, bands = (int(value) for value in fused.shape)
    return {
        "status": "success",
        "fusion_file": str(fusion_file),
        "mat_file": str(mat_file),
        "preview_file": str(preview_file),
        "width": width,
        "height": height,
        "bands": bands,
        "dtype": str(fused.dtype),
        "elapsed_seconds": time.perf_counter() - started,
        "fusion_file_size_bytes": mat_file.stat().st_size,
        "preview_file_size_bytes": preview_file.stat().st_size,
    }


def _read_spectrum(fused: np.ndarray, x: int, y: int) -> dict:
    height, width, _ = fused.shape
    if x < 0 or x >= width or y < 0 or y >= height:
        raise ValueError(
            f"coordinate out of range: x in [0,{width - 1}], y in [0,{height - 1}]"
        )
    values = np.asarray(fused[y, x, :], dtype=np.float64)
    if not np.all(np.isfinite(values)):
        raise ValueError("selected spectrum contains NaN or Inf")
    return {
        "status": "success",
        "x": x,
        "y": y,
        "spectrum": np.round(values, decimals=4).tolist(),
    }


def _run_spectrum_server(args: argparse.Namespace) -> int:
    fused = np.load(args.fusion_file.resolve(), mmap_mode="r")
    if fused.ndim != 3:
        raise ValueError("MIAE fused cube must be an H-by-W-by-B array")
    for raw_line in sys.stdin:
        if not raw_line.strip():
            continue
        try:
            request = json.loads(raw_line)
            if request.get("command") == "shutdown":
                return 0
            response = _read_spectrum(fused, int(request["x"]), int(request["y"]))
        except Exception as exception:
            response = {
                "status": "error",
                "error_type": type(exception).__name__,
                "message": str(exception),
            }
        print(json.dumps(response, ensure_ascii=False), flush=True)
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="MIAE backend fusion runner")
    commands = parser.add_subparsers(dest="command", required=True)

    fuse = commands.add_parser("fuse")
    fuse.add_argument("--input", type=Path, required=True)
    fuse.add_argument("--output-dir", type=Path, required=True)
    fuse.add_argument("--device", default="cuda")
    fuse.add_argument("--seed", type=int, default=2026)
    fuse.add_argument("--blind-iters", type=int, default=3000)
    fuse.add_argument("--fusion-iters", type=int, default=5000)
    fuse.add_argument("--batch-size", type=int, default=64)
    fuse.add_argument("--patch-size", type=int, default=64)
    fuse.add_argument("--endmembers", type=int, default=30)
    fuse.add_argument("--stages", type=int, default=3)
    fuse.add_argument("--tile-size", type=int, default=256)
    fuse.add_argument("--halo-lr", type=int, default=2)
    fuse.add_argument("--log-every", type=int, default=100)
    fuse.add_argument("--amp-inference", action="store_true")
    fuse.add_argument("--smoke-test", action="store_true")
    fuse.add_argument("--result-json", type=Path, required=True)

    spectrum_server = commands.add_parser("spectrum-server")
    spectrum_server.add_argument("--fusion-file", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.command == "spectrum-server":
            return _run_spectrum_server(args)
        result = _run_fuse(args)
        _write_json(args.result_json, result)
        return 0
    except Exception as exception:
        error = {
            "status": "error",
            "error_type": type(exception).__name__,
            "message": str(exception),
        }
        if hasattr(args, "result_json"):
            try:
                _write_json(args.result_json, error)
            except OSError:
                pass
        else:
            print(json.dumps(error, ensure_ascii=False), flush=True)
        print(f"MIAE runner failed: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
