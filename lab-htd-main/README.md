# HTD-Mamba Detection

Unified training and inference entry point for HTD-Mamba hyperspectral target
detection. With `--device auto`, the code uses `cuda:0` when PyTorch can see an
NVIDIA GPU, then Apple MPS when allowed, and finally CPU. CUDA extension builds
are optional.

## Windows NVIDIA Setup

Use Python 3.10.

### Reuse the existing `htd` environment

When the `htd` Conda environment already contains a working CUDA-enabled
PyTorch installation, install only the project runtime dependencies. Do not run
the full `requirements.txt`, because pip may download and replace the existing
Conda PyTorch build solely due to a different CUDA build suffix.

```bash
conda activate htd
python -m pip install -r requirements-runtime.txt
python -m pip check
python main.py --help
```

The checked environment uses Python 3.10, PyTorch 2.0.1 and CUDA 11.7. The
equivalent reproducible environment definition is `environment-htd.yml`.

### Create a separate environment

```bash
conda create -n htd-mamba python=3.10 -y
conda activate htd-mamba
pip install -r requirements.txt
```

Check that the laptop NVIDIA GPU is visible:

```bash
python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'no cuda')"
```

No `pip install .` is required for this default workflow. The original
`setup.py`, `csrc/`, and `mamba_ssm/` files are kept for optional Linux CUDA
extension builds. If `selective_scan_cuda` or `causal_conv1d` are installed and
importable, the code uses them automatically; otherwise it uses the pure-PyTorch
fallback.

Optional Linux CUDA acceleration:

```bash
pip install .
pip install causal_conv1d==1.4.0
```

## Data Layout

Put `.mat` files under `datasets/`, or register an absolute path in
`DATASET_REGISTRY` inside `main.py`.

Supported cube keys are:

```text
data
hyperspectral_data
X
```

Prior spectra are keys named `d1`, `d2`, ..., for example `d12`.

## Main Commands

Single-dataset training:

```bash
python main.py --state train --dataset detection1 --epoch 20
```

Joint training from a registered joint dataset:

```bash
python main.py --state train --dataset detection123_joint --epoch 5
```

Evaluate one dataset using internal priors only:

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt
```

Evaluate and fill missing priors from optional `wzcl.mat` when it exists:

```bash
python main.py --state eval --dataset detection3 --prior all --weight checkpoints/joint_ckpt.pt --fill-priors
```

Disable RBF post-processing:

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt --postprocess none
```

Change RBF gamma:

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt --postprocess-gamma 20
```

## Parameters

- `--state`: `train`, `eval`, or `select_best`.
- `--dataset`: dataset name in `DATASET_REGISTRY`, or a matching
  `datasets/<name>.mat` file.
- `--run-name`: output name for checkpoints/results. Defaults to `--dataset`.
- `--prior`: one prior such as `d12`, or `all`. Dataset priors are used first;
  add `--fill-priors` if missing priors should be filled from optional
  `wzcl.mat` when it exists.
- `--weight`: checkpoint used for evaluation. This is a `.pt` file. It can be:
  - the packaged joint checkpoint, `checkpoints/joint_ckpt.pt`;
  - an absolute path;
  - a project-relative path containing folders;
  - a file name such as `ckpt_19_.pt`, resolved under `models/<dataset>/`.
- `--result`: output `.mat` file for one prior, or output directory for
  `--prior all`.
- `--epoch`: training epochs.
- `--batch-size`: training/evaluation batch size.
- `--device`: `auto`, `cuda:0`, `cpu`, or `mps`. Default `auto`.
- `--no-mps`: when `--device auto` is used, skip Apple MPS and fall back to CPU
  if CUDA is unavailable.
- `--postprocess`: `rbf` or `none`.
- `--postprocess-gamma`: gamma for RBF background suppression.
- `--fill-priors`: use dataset priors first and fill missing prior keys from
  optional `DEFAULT_CONFIG["prior_library_path"]`. If that file is absent, the
  missing external priors are logged and skipped.

By default, `--prior all` uses only priors embedded in the dataset `.mat`.

## Outputs

The main program is silent from a UI perspective: it does not open figures or
pop up windows. It writes detection `.mat` files and matching PNG heatmaps.

Each detection `.mat` contains:

```text
detection_map
```

Default result path:

```text
results/<dataset>/detection_<prior>.mat
results/<dataset>/detection_<prior>.png
```

When using `--prior all`, an overview image is also written:

```text
results/<dataset>/detection_all_vis.png
```
