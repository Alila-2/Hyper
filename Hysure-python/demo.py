"""Fuse ``data/house.mat`` and render the final natural-colour result."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

# Keep BLAS/FFT thread counts reasonable on shared workstations. Users may
# override these variables before starting Python.
os.environ.setdefault("OMP_NUM_THREADS", str(max(1, (os.cpu_count() or 2) // 2)))
os.environ.setdefault("OPENBLAS_NUM_THREADS", os.environ["OMP_NUM_THREADS"])

from hysure import (  # noqa: E402
    FusionOptions,
    VisualizationOptions,
    run_fusion,
    visualize_fusion,
)


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="Whole-image Python HySure fusion and visualization"
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=root / "data" / "house.mat",
        help="MAT file containing LRHSI and HRMSI",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=root / "results" / "house_hysure",
    )
    parser.add_argument("--subspace", type=int, default=10)
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--block-rows", type=int, default=32)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--visualize-only",
        action="store_true",
        help="Skip fusion and visualize an existing result",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    fusion_file = args.output_dir / "dataset_final_hysure.mat"

    if not args.visualize_only:
        result = run_fusion(
            FusionOptions(
                input_file=args.input,
                output_dir=args.output_dir,
                subspace_dimension=args.subspace,
                random_seed=args.seed,
                admm_iterations=args.iterations,
                output_block_rows=args.block_rows,
                dry_run=args.dry_run,
            )
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))
        if args.dry_run:
            return 0

    visualization = visualize_fusion(
        VisualizationOptions(
            fusion_file=fusion_file,
            reference_file=args.input,
            output_file=args.output_dir / "Final_FusedHSI_TrueColor.png",
            block_rows=max(1, args.block_rows),
        )
    )
    print(json.dumps(visualization, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
