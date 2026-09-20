import argparse
import os
import time
from pathlib import Path

import scipy.io as sio
import torch

from CL_Train import train, eval, eval_multi, select_best
from PyramidSSM import print_backend_status

os.environ['CUDA_LAUNCH_BLOCKING'] = '0'
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
# Let unsupported ops fall back to CPU instead of crashing on Apple MPS.
os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

PROJECT_ROOT = Path(__file__).resolve().parent

DEFAULT_CONFIG = {
    "dataset": "Sandiego",
    "state": "eval",
    "device": "auto",
    "allow_mps": True,
    "epoch": 200,
    "batch_size": 80,
    "patch_size": 11,
    "m": 15,
    "seed": 1,
    "channel": 16,
    "lr": 1e-4,
    "multiplier": 2.0,
    "auc_precision": 10,
    "grad_clip": 1.0,
    "state_size": 16,
    "layer": 1,
    "delta": 0.1,
    "postprocess": "rbf",
    "postprocess_gamma": 10.0,
    "data_dir": str(PROJECT_ROOT / "datasets"),
    "save_dir": str(PROJECT_ROOT / "models"),
    "result_save_dir": str(PROJECT_ROOT / "results"),
    "prior_library_path": str(PROJECT_ROOT / "wzcl.mat"),
    "fill_priors": False,
}


def resolve_device(device="auto", allow_mps=True):
    '''
    Resolve runtime device by capability, not by operating system.
    Windows gaming laptops with NVIDIA GPUs normally resolve to cuda:0.
    '''
    if device != "auto":
        return device
    if torch.cuda.is_available():
        return "cuda:0"
    if allow_mps and getattr(torch.backends, "mps", None) is not None:
        if torch.backends.mps.is_available():
            return "mps"
    return "cpu"

# Optional per-dataset overrides. New lab datasets normally do not need to be
# registered: put <dataset>.mat under datasets/ and the generic DEFAULT_CONFIG
# parameters above are used. The entries below are kept only for bundled
# benchmark weights or explicitly named multi-dataset runs.
DATASET_REGISTRY = {
    "Sandiego": {"patch_size": 11, "m": 30, "test_load_weight": "ckpt_159_.pt"},
    "Sandiego2": {"patch_size": 13, "m": 5, "test_load_weight": "ckpt_12_.pt"},
    "abu-airport-2": {"patch_size": 11, "m": 5, "test_load_weight": "ckpt_187_.pt"},
    "abu-beach-4": {"patch_size": 5, "m": 15, "test_load_weight": "ckpt_180_.pt"},
    "detection123_joint": {
        "patch_size": 11,
        "m": 15,
        "test_load_weight": None,
        "train_datasets": ["detection1", "detection2", "detection3"],
    },
    # detection4 is EXCLUDED from joint presets: it is a 64-band cube but ships
    # a 320-dim d1 and 64-dim d2-d8. Run it explicitly only after confirming
    # which priors are valid for that source file.
}

DATASET_DIR = DEFAULT_CONFIG["data_dir"]

# ---------------------------------------------------------------------------
# Default run configuration (used when NO command-line arguments are given).
# Just edit these values and run `python main.py` directly (e.g. from an IDE
# "Run" button). Passing --dataset / --state on the command line overrides
# whatever is set here.
# ---------------------------------------------------------------------------
DEFAULT_DATASET = DEFAULT_CONFIG["dataset"]
DEFAULT_STATE = DEFAULT_CONFIG["state"]  # one of: train / eval / select_best


def _resolve_project_path(path):
    if path is None:
        return None
    resolved = Path(path).expanduser()
    if resolved.is_absolute():
        return str(resolved)
    return str(PROJECT_ROOT / resolved)


def read_band_from_mat(mat_path):
    '''
    Read the number of spectral bands from a .mat file. The hyperspectral cube is
    stored under one of Utils.DATA_KEYS, with shape
    (height, width, band).
    '''
    from Utils import DATA_KEYS

    for name, shape, _class_name in sio.whosmat(mat_path):
        if name in DATA_KEYS:
            return int(shape[-1])
    raise KeyError(
        "None of the expected data keys %s found in the .mat."
        % (list(DATA_KEYS),)
    )


def _has_internal_priors(mat_path):
    for name, _shape, _class_name in sio.whosmat(mat_path):
        if name.startswith("d") and name[1:].isdigit():
            return True
    return False


def _resolve_dataset_path(dataset):
    dataset_params = DATASET_REGISTRY.get(dataset, {})
    if dataset_params.get("path"):
        return _resolve_project_path(dataset_params["path"])
    return os.path.join(DATASET_DIR, dataset + ".mat")


def _resolve_weight_path(save_dir, dataset, weight):
    if weight is None:
        return None
    if os.path.isabs(weight):
        return weight
    if os.path.dirname(weight):
        return _resolve_project_path(weight)
    return os.path.join(save_dir, dataset, weight)


def _scan_dataset_dir(datasets_dir, train_band=None):
    datasets_dir = _resolve_project_path(datasets_dir)
    paths = sorted(str(p) for p in Path(datasets_dir).glob("*.mat"))
    if not paths:
        raise FileNotFoundError("No .mat files found under '%s'." % datasets_dir)

    selected = []
    skipped = []
    target_band = train_band
    for path in paths:
        try:
            band = read_band_from_mat(path)
        except Exception as exc:
            skipped.append((path, "unreadable: %s" % exc))
            continue
        if target_band is None:
            target_band = band
        if band != target_band:
            skipped.append((path, "band %d != target_band %d" % (band, target_band)))
            continue
        selected.append(path)

    for path in selected:
        print("[datasets] include %s band=%d" % (path, target_band))
    for path, reason in skipped:
        print("[datasets] skip %s (%s)" % (path, reason))
    if not selected:
        raise ValueError("No usable .mat files found under '%s'." % datasets_dir)
    return selected, int(target_band)


def build_config(dataset, state, prior=None, weight=None, result_path=None,
                 epoch=None, postprocess=None, postprocess_gamma=None,
                 batch_size=None, patch_size=None, m=None,
                 max_train_samples=None,
                 max_train_samples_per_dataset=None, eval_max_pixels=None,
                 use_prior_library_fallback=None, device=None, allow_mps=None,
                 datasets_dir=None, run_name=None, train_band=None):
    '''
    Assemble the full model configuration from a dataset name and a run state.
    - path is composed from the dataset name
    - band is auto-read from the .mat file
    - patch_size / m come from the registry (or defaults for unknown datasets)
    - prior selects a target spectrum d1..d255 (None -> derive from gt)
    - weight overrides the checkpoint file used for eval
    - result_path overrides where the detection .mat is written
    - postprocess optionally applies RBF background suppression to the score map
    '''
    if datasets_dir is not None and state != "train":
        raise ValueError("--datasets is only supported with --state train.")
    resolved_epoch = epoch if epoch is not None else DEFAULT_CONFIG["epoch"]
    resolved_batch_size = (
        batch_size if batch_size is not None else DEFAULT_CONFIG["batch_size"]
    )
    if resolved_epoch <= 0:
        raise ValueError("--epoch must be a positive integer.")
    if resolved_batch_size <= 0:
        raise ValueError("--batch-size must be a positive integer.")
    if patch_size is not None and patch_size <= 0:
        raise ValueError("--patch-size must be a positive integer.")
    if m is not None and m <= 0:
        raise ValueError("--m must be a positive integer.")
    if eval_max_pixels is not None and eval_max_pixels <= 0:
        raise ValueError("--eval-max-pixels must be a positive integer.")
    if max_train_samples is not None and max_train_samples <= 0:
        raise ValueError("--max-train-samples must be a positive integer.")
    if (
        max_train_samples_per_dataset is not None
        and max_train_samples_per_dataset <= 0
    ):
        raise ValueError("--max-train-samples-per-dataset must be a positive integer.")

    source_dataset = dataset
    output_name = run_name or dataset

    if datasets_dir is not None:
        datasets_dir = _resolve_project_path(datasets_dir)
        train_paths, band = _scan_dataset_dir(datasets_dir, train_band=train_band)
        train_datasets = [Path(p).stem for p in train_paths]
        dataset_params = {}
        source_dataset = Path(datasets_dir).name
        output_name = run_name or Path(datasets_dir).name
        path = train_paths[0]
    else:
        dataset_params = DATASET_REGISTRY.get(dataset, {})
        train_datasets = dataset_params.get("train_datasets", None)

    if datasets_dir is None:
        if train_datasets:
            train_paths = [_resolve_dataset_path(name) for name in train_datasets]
            missing = [p for p in train_paths if not os.path.exists(p)]
            if missing:
                raise FileNotFoundError(
                    "Joint-training dataset file(s) not found: %s"
                    % ", ".join(missing)
                )
            bands = [read_band_from_mat(p) for p in train_paths]
            if len(set(bands)) != 1:
                raise ValueError(
                    "Joint-training datasets must have the same band count: %s"
                    % dict(zip(train_datasets, bands))
                )
            path = train_paths[0]
            band = bands[0]
        else:
            train_paths = None
            path = _resolve_dataset_path(dataset)
            if not os.path.exists(path):
                raise FileNotFoundError(
                    "Dataset file not found: %s. Put <dataset>.mat under the '%s' folder."
                    % (path, DATASET_DIR)
                )
            band = read_band_from_mat(path)

    if not os.path.exists(path):
        raise FileNotFoundError(
            "Dataset file not found: %s. Put <dataset>.mat under the '%s' folder."
            % (path, DATASET_DIR)
        )

    resolved_patch_size = (
        patch_size
        if patch_size is not None
        else dataset_params.get("patch_size", DEFAULT_CONFIG["patch_size"])
    )
    resolved_m = (
        m
        if m is not None
        else dataset_params.get("m", DEFAULT_CONFIG["m"])
    )
    if resolved_patch_size % 2 == 0:
        raise ValueError("--patch-size must be odd so each patch has a center pixel.")
    if resolved_m > band:
        raise ValueError(
            "--m (%d) must be <= dataset band count (%d)." % (resolved_m, band)
        )
    # A checkpoint passed on the command line (--weight) overrides the registry.
    test_load_weight = weight or dataset_params.get("test_load_weight", None)

    if test_load_weight is None and state in ("eval",):
        raise ValueError(
            "No checkpoint for dataset '%s'. Train it first (state=train), then "
            "evaluate with --weight <ckpt_xx_.pt> (or register test_load_weight)."
            % dataset
        )

    config = {
        "state": state,
        "epoch": resolved_epoch,
        "band": band,
        "batch_size": resolved_batch_size,
        "seed": DEFAULT_CONFIG["seed"],
        "channel": DEFAULT_CONFIG["channel"],
        "lr": DEFAULT_CONFIG["lr"],
        "multiplier": DEFAULT_CONFIG["multiplier"],
        "epision": DEFAULT_CONFIG["auc_precision"],
        "grad_clip": DEFAULT_CONFIG["grad_clip"],
        "device": resolve_device(
            DEFAULT_CONFIG["device"] if device is None else device,
            DEFAULT_CONFIG["allow_mps"] if allow_mps is None else allow_mps,
        ),
        "training_load_weight": None,
        "save_dir": DEFAULT_CONFIG["save_dir"],
        "result_save_dir": DEFAULT_CONFIG["result_save_dir"],
        "test_load_weight": test_load_weight,
        "test_load_weight_path": _resolve_weight_path(
            DEFAULT_CONFIG["save_dir"], output_name, test_load_weight
        ),
        "patch_size": resolved_patch_size,
        "m": resolved_m,
        "state_size": DEFAULT_CONFIG["state_size"],
        "layer": DEFAULT_CONFIG["layer"],
        "delta": DEFAULT_CONFIG["delta"],
        "postprocess": DEFAULT_CONFIG["postprocess"] if postprocess is None else postprocess,
        "postprocess_gamma": DEFAULT_CONFIG["postprocess_gamma"] if postprocess_gamma is None else postprocess_gamma,
        "dataset": output_name,
        "source_dataset": source_dataset,
        "path": path,
        "train_datasets": train_datasets,
        "train_paths": train_paths,
        "max_train_samples": max_train_samples,
        "max_train_samples_per_dataset": max_train_samples_per_dataset,
        "eval_max_pixels": eval_max_pixels,
        # Prior selector. Production detection should pass dN or all:
        # - without --fill-priors: use dataset-embedded priors only
        # - with --fill-priors: use dataset priors first, fill missing keys from
        #   the shared library. None is only the legacy benchmark path that
        #   derives one spectrum from the GT map.
        "prior": prior,
        "prior_library_path": DEFAULT_CONFIG["prior_library_path"],
        "use_prior_library_fallback": DEFAULT_CONFIG["fill_priors"],
        # Single-class eval: full output .mat path. None -> auto path.
        "result_path": _resolve_project_path(result_path),
        # Multi-prior eval_multi: output directory for detection_d{k}.mat files.
        # Reuses --result as a directory when --prior all is set.
        "result_dir": _resolve_project_path(result_path),
    }
    config["use_prior_library_fallback"] = dataset_params.get(
        "use_prior_library_fallback",
        config["use_prior_library_fallback"],
    )
    if use_prior_library_fallback is not None:
        config["use_prior_library_fallback"] = use_prior_library_fallback
    return config


def run(dataset, state, prior=None, weight=None, result_path=None, epoch=None,
        postprocess=None, postprocess_gamma=None,
        batch_size=None, patch_size=None, m=None, max_train_samples=None,
        max_train_samples_per_dataset=None, eval_max_pixels=None,
        use_prior_library_fallback=None, device=None, allow_mps=None,
        datasets_dir=None, run_name=None, train_band=None):
    config = build_config(dataset, state, prior=prior, weight=weight,
                          result_path=result_path, epoch=epoch,
                          postprocess=postprocess,
                          postprocess_gamma=postprocess_gamma,
                          batch_size=batch_size,
                          patch_size=patch_size,
                          m=m,
                          max_train_samples=max_train_samples,
                          max_train_samples_per_dataset=max_train_samples_per_dataset,
                          eval_max_pixels=eval_max_pixels,
                          use_prior_library_fallback=use_prior_library_fallback,
                          device=device,
                          allow_mps=allow_mps,
                          datasets_dir=datasets_dir,
                          run_name=run_name,
                          train_band=train_band)
    if state == "eval" and config.get("prior") is None:
        if _has_internal_priors(config["path"]) or config.get(
            "use_prior_library_fallback", False
        ):
            config["prior"] = "all"

    print_backend_status(config["device"])
    print(
        "[HTD-Mamba] dataset=%s | run=%s | state=%s | band=%d | patch_size=%d | m=%d | prior=%s | postprocess=%s(gamma=%s)"
        % (
            config["source_dataset"],
            config["dataset"],
            state,
            config["band"],
            config["patch_size"],
            config["m"],
            config["prior"],
            config["postprocess"],
            config["postprocess_gamma"],
        )
    )
    if state == "train":
        train(config)
    elif state == "select_best":
        select_best(config)
    elif str(config.get("prior")).lower() == "all":
        # Shared-inference multi-prior detection: run every available d1..d255
        # in a single process, reusing one full-image inference across priors.
        eval_multi(config)
    else:
        eval(config)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Unified entry point for HTD-Mamba. Pick a dataset and a mode."
    )
    parser.add_argument(
        "--dataset",
        "-d",
        default=DEFAULT_DATASET,
        help="Dataset name, e.g. Sandiego, Sandiego2, abu-airport-2, abu-beach-4, "
             "D_20251023_132740. The corresponding <dataset>.mat must exist "
             "under the datasets folder. If omitted, falls back to DEFAULT_DATASET.",
    )
    parser.add_argument(
        "--datasets",
        default=None,
        help="Directory of .mat files for joint training. Incompatible band counts are logged and skipped.",
    )
    parser.add_argument(
        "--run-name",
        default=None,
        help="Model/output run name. Defaults to --dataset, or the datasets folder name when using --datasets.",
    )
    parser.add_argument(
        "--train-band",
        type=int,
        default=None,
        help="Optional required band count when scanning --datasets.",
    )
    parser.add_argument(
        "--state",
        "-s",
        default=DEFAULT_STATE,
        choices=["train", "eval", "select_best"],
        help="Run mode: train / eval / select_best. If omitted, falls back to "
             "DEFAULT_STATE defined at the top of main.py.",
    )
    parser.add_argument(
        "--prior",
        "-p",
        default=None,
        help="Target-spectrum selector: one prior such as d3, or all. By "
             "default, eval uses dataset-embedded priors when present. Add "
             "--fill-priors to fill missing priors from optional wzcl.mat when "
             "it exists. If no prior is supplied and the dataset has no embedded dN priors, one "
             "spectrum is derived from the ground-truth map for legacy "
             "benchmark evaluation.",
    )
    parser.add_argument(
        "--weight",
        "-w",
        default=None,
        help="Checkpoint file name under models/<dataset>/ to load for eval "
             "(e.g. --weight ckpt_50_.pt). Overrides the registry default.",
    )
    parser.add_argument(
        "--result",
        "-r",
        default=None,
        help="Output path for one-prior detection, or output directory for "
             "--prior all. A .mat detection map and matching .png heatmap are "
             "saved.",
    )
    parser.add_argument(
        "--epoch",
        "-e",
        type=int,
        default=None,
        help="Number of training epochs (also the range scanned by "
             "select_best). Defaults to 200 if omitted.",
    )
    parser.add_argument(
        "--postprocess",
        default=None,
        choices=["rbf", "none"],
        help="Detection-map post-processing. Default: rbf background suppression.",
    )
    parser.add_argument(
        "--postprocess-gamma",
        type=float,
        default=None,
        help="Gamma for RBF background suppression when --postprocess rbf.",
    )
    parser.add_argument(
        "--device",
        default=DEFAULT_CONFIG["device"],
        help="Runtime device: auto, cuda:0, cpu, or mps. Default: auto.",
    )
    parser.add_argument(
        "--no-mps",
        action="store_true",
        help="When --device auto is used, skip Apple MPS and fall back to CPU if CUDA is unavailable.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=None,
        help="Override inference/training batch size. Larger values speed up full-scene eval if memory allows.",
    )
    parser.add_argument(
        "--patch-size",
        type=int,
        default=None,
        help="Patch window size for training. Default comes from DEFAULT_CONFIG.",
    )
    parser.add_argument(
        "--m",
        type=int,
        default=None,
        help="Spectral group length used by the model. Train and eval must use the same value.",
    )
    parser.add_argument(
        "--eval-max-pixels",
        type=int,
        default=None,
        help="Optional debug cap for eval. Omit for full-scene testing.",
    )
    parser.add_argument(
        "--fill-priors",
        action="store_true",
        help="Fill missing dataset priors from optional wzcl.mat when present; missing library priors are skipped.",
    )
    parser.add_argument(
        "--max-train-samples",
        type=int,
        default=None,
        help="Optional debug cap for single-dataset training.",
    )
    parser.add_argument(
        "--max-train-samples-per-dataset",
        type=int,
        default=None,
        help="Optional debug cap for joint training; samples this many pixels "
             "from each training dataset before concatenating.",
    )
    return parser.parse_args()


if __name__ == '__main__':
    args = parse_args()
    start = time.perf_counter()
    run(args.dataset, args.state, prior=args.prior, weight=args.weight,
        result_path=args.result, epoch=args.epoch,
        postprocess=args.postprocess,
        postprocess_gamma=args.postprocess_gamma,
        batch_size=args.batch_size,
        patch_size=args.patch_size,
        m=args.m,
        max_train_samples=args.max_train_samples,
        max_train_samples_per_dataset=args.max_train_samples_per_dataset,
        eval_max_pixels=args.eval_max_pixels,
        use_prior_library_fallback=True if args.fill_priors else None,
        device=args.device,
        allow_mps=not args.no_mps,
        datasets_dir=args.datasets,
        run_name=args.run_name,
        train_band=args.train_band)
    end = time.perf_counter()
    print('time is %s' % (end - start))
