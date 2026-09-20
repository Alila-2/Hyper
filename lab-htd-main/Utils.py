import numpy as np
import os
import scipy.io as sio

# The hyperspectral cube may be stored under either of these keys.
DATA_KEYS = ("data", "hyperspectral_data", "X")

def standard(X):
    '''
    Standardization
    universal
    :param X:
    :return:
    '''
    min_x = np.min(X)
    max_x = np.max(X)
    if min_x == max_x:
        return np.zeros_like(X)
    return np.float32((X - min_x) / (max_x - min_x))

def load_cube(mat_or_path):
    '''
    Load the hyperspectral cube from a .mat path or an already-loaded mat dict,
    accepting either the 'data' or 'hyperspectral_data' key.

    Returns (cube, mat) where cube has shape (H, W, band) and mat is the loaded
    dict (so callers can also fetch 'map' / prior keys from it).
    '''
    mat = sio.loadmat(mat_or_path) if isinstance(mat_or_path, str) else mat_or_path
    for key in DATA_KEYS:
        if key in mat:
            return mat[key], mat
    raise KeyError(
        "None of the expected data keys %s found in the .mat. Available keys: %s"
        % (list(DATA_KEYS), [k for k in mat if not k.startswith('__')])
    )

def standard_with_range(X):
    '''
    Same global min-max normalisation as `standard`, but also returns the raw
    (min, max) used, so an externally supplied prior spectrum can be mapped into
    the exact same coordinate system as the normalised cube.
    '''
    min_x = np.min(X)
    max_x = np.max(X)
    if min_x == max_x:
        return np.zeros_like(X), float(min_x), float(max_x)
    normalised = np.float32((X - min_x) / (max_x - min_x))
    return normalised, float(min_x), float(max_x)

def checkFile(path):
    '''
    if filepath not exist make it
    :param path:
    :return:
    '''
    if not os.path.exists(path):
        os.makedirs(path)
