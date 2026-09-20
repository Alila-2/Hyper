"""Machine-readable command-line bridge for the Spring Boot backend."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

# Limit native worker pools unless deployment configuration overrides them.
os.environ.setdefault("OMP_NUM_THREADS", str(max(1, (os.cpu_count() or 2) // 2)))
os.environ.setdefault("OPENBLAS_NUM_THREADS", os.environ["OMP_NUM_THREADS"])

import h5py  # noqa: E402
import numpy as np  # noqa: E402

from hysure import (  # noqa: E402
    FusionOptions,
    VisualizationOptions,
    run_fusion,
    visualize_fusion,
)


def _write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    temporary.replace(path)


def _read_fusion_info(fusion_file: Path) -> dict:
    if not fusion_file.is_file():
        raise FileNotFoundError(fusion_file)
    with h5py.File(fusion_file, "r") as handle:
        if "FusedHSI" not in handle:
            raise KeyError(f"{fusion_file} does not contain FusedHSI")
        fused = handle["FusedHSI"]
        if fused.ndim != 3:
            raise ValueError("FusedHSI must be an H-by-W-by-B cube")
        height, width, bands = (int(value) for value in fused.shape)
        return {
            "fusion_file": str(fusion_file.resolve()),
            "width": width,
            "height": height,
            "bands": bands,
            "dtype": str(fused.dtype),
        }


def _run_fuse(args: argparse.Namespace) -> dict:
    output_dir = args.output_dir.resolve()
    result = run_fusion(
        FusionOptions(
            input_file=args.input.resolve(),
            output_dir=output_dir,
            subspace_dimension=args.subspace,
            random_seed=args.seed,
            blur_support=args.blur_support,
            admm_iterations=args.iterations,
            output_block_rows=args.block_rows,
        )
    )
    fusion_file = Path(result["output_file"])
    preview_file = output_dir / "Final_FusedHSI_TrueColor.png"
    info = _read_fusion_info(fusion_file)
    sample_step = max(1, min(16, min(info["height"], info["width"]) // 10))
    visualization = visualize_fusion(
        VisualizationOptions(
            fusion_file=fusion_file,
            reference_file=args.input.resolve(),
            output_file=preview_file,
            block_rows=max(1, args.block_rows),
            sample_step=sample_step,
        )
    )
    info.update(
        {
            "status": "success",
            "preview_file": visualization["output_file"],
            "elapsed_seconds": float(result["elapsed_seconds"]),
            "fusion_file_size_bytes": fusion_file.stat().st_size,
            "preview_file_size_bytes": Path(
                visualization["output_file"]
            ).stat().st_size,
        }
    )
    return info


def _run_info(args: argparse.Namespace) -> dict:
    result = _read_fusion_info(args.fusion_file.resolve())
    result["status"] = "success"
    return result


def _run_spectrum(args: argparse.Namespace) -> dict:
    fusion_file = args.fusion_file.resolve()
    with h5py.File(fusion_file, "r") as handle:
        if "FusedHSI" not in handle:
            raise KeyError(f"{fusion_file} does not contain FusedHSI")
        fused = handle["FusedHSI"]
        if fused.ndim != 3:
            raise ValueError("FusedHSI must be an H-by-W-by-B cube")
        return _read_spectrum(fused, args.x, args.y)


def _read_spectrum(fused: h5py.Dataset, x: int, y: int) -> dict:
    height, width, _ = fused.shape
    if x < 0 or x >= width or y < 0 or y >= height:
        raise ValueError(
            f"coordinate out of range: x in [0,{width - 1}], "
            f"y in [0,{height - 1}]"
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
    fusion_file = args.fusion_file.resolve()
    with h5py.File(fusion_file, "r") as handle:
        if "FusedHSI" not in handle:
            raise KeyError(f"{fusion_file} does not contain FusedHSI")
        fused = handle["FusedHSI"]
        if fused.ndim != 3:
            raise ValueError("FusedHSI must be an H-by-W-by-B cube")
        for raw_line in sys.stdin:
            if not raw_line.strip():
                continue
            try:
                request = json.loads(raw_line)
                if request.get("command") == "shutdown":
                    return 0
                response = _read_spectrum(
                    fused,
                    int(request["x"]),
                    int(request["y"]),
                )
            except Exception as exception:  # keep the worker alive per request
                response = {
                    "status": "error",
                    "error_type": type(exception).__name__,
                    "message": str(exception),
                }
            print(json.dumps(response, ensure_ascii=False), flush=True)
    return 0


def _add_result_json(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--result-json", type=Path, required=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="HySure backend runner")
    commands = parser.add_subparsers(dest="command", required=True)

    fuse = commands.add_parser("fuse", help="Run fusion and visualization")
    fuse.add_argument("--input", type=Path, required=True)
    fuse.add_argument("--output-dir", type=Path, required=True)
    fuse.add_argument("--subspace", type=int, default=10)
    fuse.add_argument("--iterations", type=int, default=30)
    fuse.add_argument("--seed", type=int, default=0)
    fuse.add_argument("--blur-support", type=int, default=10)
    fuse.add_argument("--block-rows", type=int, default=32)
    _add_result_json(fuse)

    info = commands.add_parser("info", help="Inspect a fused HDF5 file")
    info.add_argument("--fusion-file", type=Path, required=True)
    _add_result_json(info)

    spectrum = commands.add_parser("spectrum", help="Read one fused spectrum")
    spectrum.add_argument("--fusion-file", type=Path, required=True)
    spectrum.add_argument("--x", type=int, required=True)
    spectrum.add_argument("--y", type=int, required=True)
    _add_result_json(spectrum)

    spectrum_server = commands.add_parser(
        "spectrum-server", help="Serve spectrum queries over JSON lines"
    )
    spectrum_server.add_argument("--fusion-file", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.command == "spectrum-server":
            return _run_spectrum_server(args)
        if args.command == "fuse":
            result = _run_fuse(args)
        elif args.command == "info":
            result = _run_info(args)
        else:
            result = _run_spectrum(args)
        _write_json(args.result_json, result)
        return 0
    except Exception as exception:  # command boundary: return structured errors
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
        print(f"HySure runner failed: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
