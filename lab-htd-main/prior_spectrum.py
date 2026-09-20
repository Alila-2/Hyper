"""
Prior target-spectrum resolution for HTD-Mamba.

In real deployments there may be no ground-truth label map to derive the target
spectrum from. Instead the target spectrum can be stored in the dataset itself
as one of the named priors d1..d255. This module resolves priors by index
following a fixed priority:

    1. If the dataset .mat itself contains the key (e.g. 'd3'), use it
       (scene-specific prior, most accurate).
    2. If library fallback is enabled and wzcl.mat exists, fill missing priors
       from that optional library.
    3. If neither has it, log and skip that prior.

The single-prior contract: each saved detection .mat corresponds to ONE prior
spectrum. `main.py --prior all` iterates over all available priors in one
process while sharing the expensive full-image inference.
"""
import os

import numpy as np
import scipy.io as sio

# Number of named priors supported: d1 .. d255 (map is uint8).
NUM_PRIORS = 255
PRIOR_LIBRARY_FILENAME = "wzcl.mat"


def _valid_prior_keys():
    return {"d%d" % i for i in range(1, NUM_PRIORS + 1)}


def _extract_spectrum(mat, key):
    '''
    Pull a single prior spectrum out of a loaded .mat dict and normalise it to a
    1-D float32 vector of shape (band,). Accepts (band,), (band,1) or (1,band).
    '''
    raw = np.asarray(mat[key]).squeeze()
    if raw.ndim != 1:
        raise ValueError(
            "Prior '%s' is expected to be a 1-D spectrum vector, got shape %s"
            % (key, np.asarray(mat[key]).shape)
        )
    return raw.astype(np.float32)


def _load_mat(mat_or_path):
    if isinstance(mat_or_path, dict):
        return mat_or_path
    return sio.loadmat(mat_or_path)


def _load_optional_library(library_path):
    if library_path is None:
        library_path = PRIOR_LIBRARY_FILENAME
    if not os.path.exists(library_path):
        print(
            "[prior] fallback library '%s' not found; skip external priors."
            % library_path
        )
        return None
    try:
        return sio.loadmat(library_path)
    except Exception as exc:
        print(
            "[prior] fallback library '%s' unreadable (%s); skip external priors."
            % (library_path, exc)
        )
        return None


def resolve_prior_spectrum(dataset_mat_or_path, prior_index, library_path=None,
                           use_library_fallback=True):
    '''
    Resolve prior spectrum `d{prior_index}` for a given dataset.

    dataset_mat_or_path: path to the dataset .mat, or an already-loaded mat dict
                         that may embed its own d1..d255
    prior_index:  either an int (1..16) or a string like 'd3'
    library_path: path to the fallback prior library (defaults to wzcl.mat next
                  to this file / project root)

    Returns a float32 numpy array of shape (band,), or None when the requested
    prior cannot be found. Missing fallback libraries are treated as optional
    and never fail the whole detection run.
    '''
    key = _normalise_prior_key(prior_index)

    dataset_mat = _load_mat(dataset_mat_or_path)
    if key in dataset_mat:
        try:
            return _extract_spectrum(dataset_mat, key)
        except ValueError as exc:
            print("[prior] skip '%s' in dataset: %s" % (key, exc))
            return None

    if not use_library_fallback:
        print("[prior] skip '%s': not present in dataset." % key)
        return None

    library_mat = _load_optional_library(library_path)
    if library_mat is None:
        print("[prior] skip '%s': not present in dataset and no fallback library." % key)
        return None
    if key not in library_mat:
        print("[prior] skip '%s': not present in dataset or fallback library." % key)
        return None
    try:
        return _extract_spectrum(library_mat, key)
    except ValueError as exc:
        print("[prior] skip '%s' in fallback library: %s" % (key, exc))
        return None


def _normalise_prior_key(prior_index):
    if isinstance(prior_index, str):
        key = prior_index.strip().lower()
        if not key.startswith("d"):
            key = "d" + key
    else:
        key = "d%d" % int(prior_index)

    if key not in _valid_prior_keys():
        raise ValueError(
            "Invalid prior index '%s'. Expected one of d1..d%d."
            % (prior_index, NUM_PRIORS)
        )
    return key

def resolve_all_priors(dataset_mat_or_path, library_path=None, expected_band=None,
                       use_library_fallback=False):
    '''
    Collect the available priors d1..d255 for a dataset.

    use_library_fallback: when True, priors missing from the dataset are filled
                   from the optional shared library (wzcl.mat) if it exists.
                   Dataset keys always win over library keys with the same name.

    expected_band: if given, priors whose length does not match the cube's band
                   count are skipped with a warning (e.g. a 320-dim prior stored
                   next to a 64-band cube). This guards against mismatched or
                   corrupt entries.

    Returns a list of (key, spectrum) tuples in d1..d255 order, where spectrum is
    a float32 array of shape (band,). Returns an empty list when no usable prior
    exists. `dataset_mat_or_path` can be a path or an already-loaded mat dict.
    '''
    dataset_mat = _load_mat(dataset_mat_or_path)

    library_mat = None
    if use_library_fallback:
        library_mat = _load_optional_library(library_path)

    dataset_keys = {
        key for key in dataset_mat
        if key.startswith("d") and key[1:].isdigit()
    }
    library_keys = set()
    if library_mat is not None:
        library_keys = {
            key for key in library_mat
            if key.startswith("d") and key[1:].isdigit()
        }

    priors = []
    candidate_keys = sorted(
        dataset_keys | (library_keys if use_library_fallback else set()),
        key=lambda key: int(key[1:]),
    )

    for key in candidate_keys:
        if key in dataset_mat:
            source = "dataset"
            mat = dataset_mat
        elif library_mat is not None and key in library_mat:
            source = "fallback library"
            mat = library_mat
        else:
            continue

        try:
            spectrum = _extract_spectrum(mat, key)
        except ValueError as exc:
            print("[prior] skip '%s' from %s: %s" % (key, source, exc))
            continue
        if expected_band is not None and spectrum.shape[0] != expected_band:
            print(
                "[prior] skip '%s' from %s: length %d != cube band %d"
                % (key, source, spectrum.shape[0], expected_band)
            )
            continue
        priors.append((key, spectrum))

    if not priors:
        print(
            "[prior] no usable priors d1..d255 (band=%s) found in dataset%s; skip detection."
            % (expected_band, " or fallback library" if use_library_fallback else "")
        )
    return priors

def normalize_prior_to_data(spectrum, data_min, data_max):
    '''
    Map an externally supplied prior spectrum into the SAME coordinate system as
    the normalised cube.

    The cube is normalised with a global min-max: (X - data_min)/(data_max -
    data_min). An external prior stored in raw units (e.g. 0..65535) must undergo
    the identical affine transform, otherwise it enters the (non-linear) model at
    a wildly different scale than the pixels it is compared against, distorting
    the extracted features.

    Returns a float32 array of the same shape as `spectrum`.
    '''
    spectrum = np.asarray(spectrum, dtype=np.float64)
    if data_max == data_min:
        return np.zeros_like(spectrum, dtype=np.float32)
    normalised = (spectrum - data_min) / (data_max - data_min)
    return np.clip(normalised, 0.0, 1.0).astype(np.float32)
