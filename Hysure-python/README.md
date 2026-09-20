# HySure Python

Python port of the repository's MATLAB whole-image HySure workflow. The demo
reads `data/house.mat`, fuses `LRHSI` with `HRMSI`, writes the 320-band result
incrementally, and creates a natural-colour PNG using the estimated spectral
response.

## Environment

Use 64-bit Python 3.10 or newer. A machine with at least 24 GB RAM is strongly
recommended; 32 GB is safer for the supplied 512x512x320 / 1024x1024x3 data.

```powershell
cd F:\LiuTianyi\HySure-master\Hysure-python
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

## Run

First validate the input without running fusion:

```powershell
python demo.py --dry-run
```

Run complete fusion and visualization:

```powershell
python demo.py
```

Run the small synthetic numerical smoke test:

```powershell
python tests\smoke_test.py
```

Compare a completed result with the existing MATLAB v7.3 baseline:

```powershell
python scripts\compare_matlab.py `
  ..\result\dataset_final_hysure2\dataset_final_hysure.mat `
  results\house_hysure\dataset_final_hysure.mat
```

For a quick algorithm smoke test, reduce ADMM iterations (this is not a final
scientific result):

```powershell
python demo.py --iterations 2
```

Visualize an existing Python result without re-running fusion:

```powershell
python demo.py --visualize-only
```

## Spring Boot integration

`hysure_runner.py` is the machine-readable bridge used by
`hyperspectral-server`. It supports complete fusion, HDF5 metadata inspection,
and one-pixel spectrum reads without loading the fused cube into the JVM.

The backend uses the `htd` Conda environment by default:

```powershell
conda run --no-capture-output -n htd python tests\runner_test.py
```

Runtime settings are exposed under `fusion.hysure` in the backend
`application.yml`. Set `HYPER_HYSURE_PYTHON` to an explicit interpreter when
Conda is not available to the service process. Set `HYPER_HYSURE_ENABLED=false`
to retain the Java CNMF implementation as a fallback.

Outputs are written to `results/house_hysure/`:

- `dataset_final_hysure.mat`: MATLAB v7.3 file containing `FusedHSI`, `R_est`,
  `B_est`, and `scaleInfo`. The file is written directly in chunks; no `.h5`
  result is retained.
- `Final_FusedHSI_TrueColor.png`: affine-corrected natural-colour preview.
- `Final_FusedHSI_TrueColor_R_est_uncorrected.png`: direct `R_est` projection.
- JSON/NPZ files containing run metadata and colour settings.

The large result uses MATLAB v7.3, whose storage layer is HDF5. MATLAB can load
it directly or access `FusedHSI` incrementally with `matfile`; the Python
visualizer also reads it in row blocks.

## Verified production-data run

The supplied `data/house.mat` has been run end to end with 30 ADMM iterations.
The verified output is `1024x1024x320` `float32`, contains no NaN/Inf values,
and took about 171 seconds in the available `torch21` Python environment. The
observed Python private-memory footprint was roughly 4.5 GiB during ADMM.

Compared blockwise with the existing MATLAB result, `R_est` RMSE was
`1.28e-6`, `B_est` RMSE was `2.01e-10`, and fused-cube relative RMSE was about
`2.56%`. The cube difference is expected to be larger than the response-model
difference because VCA is stochastic and MATLAB and NumPy use different random
generators and eigensolver implementations.

## Numerical notes

- MATLAB's one-based visible-band ranges are converted explicitly to Python's
  zero-based indexing.
- Array reshapes use MATLAB column-major ordering.
- The high-resolution 320-band zero-filled intermediate is avoided by doing
  the mathematically equivalent upsampling in the 10-dimensional subspace.
- The final 320-band cube is reconstructed and written in row blocks.
- VCA is stochastic. `--seed 0` makes Python runs reproducible, but MATLAB and
  NumPy use different random generators, so bitwise MATLAB equality is not
  expected. Validate using numerical tolerances and image-quality metrics.
