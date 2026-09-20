"""MATLAB input and chunked MATLAB v7.3 result I/O."""

from __future__ import annotations

import json
import sys
from datetime import datetime
from pathlib import Path

import h5py
import numpy as np
from scipy.io import loadmat


def _decode_matlab_hdf5(dataset: h5py.Dataset) -> np.ndarray:
    array = np.asarray(dataset)
    if dataset.attrs.get("storage_order", b"") == b"python":
        return array
    if array.ndim > 1:
        array = array.transpose(tuple(range(array.ndim - 1, -1, -1)))
    return np.ascontiguousarray(array)


def matlab_shape(dataset: h5py.Dataset) -> tuple[int, ...]:
    """Return a dataset shape in MATLAB/Python logical dimension order."""
    if dataset.attrs.get("storage_order", b"") == b"python":
        return tuple(int(value) for value in dataset.shape)
    return tuple(int(value) for value in reversed(dataset.shape))


def read_matlab_array(dataset: h5py.Dataset) -> np.ndarray:
    """Read a numeric MATLAB dataset and restore its logical dimensions."""
    return _decode_matlab_hdf5(dataset)


def read_cube_rows(
    dataset: h5py.Dataset, rows: slice | np.ndarray
) -> np.ndarray:
    """Read selected H rows from an H-by-W-by-B cube without loading it all."""
    if dataset.attrs.get("storage_order", b"") == b"python":
        return np.asarray(dataset[rows, :, :])
    stored = np.asarray(dataset[:, :, rows])
    return np.ascontiguousarray(stored.transpose(2, 1, 0))


def read_cube_pixel(dataset: h5py.Dataset, x: int, y: int) -> np.ndarray:
    """Read one spectrum from an H-by-W-by-B cube."""
    if dataset.attrs.get("storage_order", b"") == b"python":
        return np.asarray(dataset[y, x, :])
    return np.asarray(dataset[:, x, y])


def read_scale_info(group: h5py.Group) -> dict[str, float]:
    """Read scale metadata from either legacy attributes or MAT struct fields."""
    if group.attrs.get("MATLAB_class", b"") == b"struct":
        return {
            name: float(np.asarray(value).reshape(-1)[0])
            for name, value in group.items()
        }
    return {name: float(value) for name, value in group.attrs.items()}


def _write_matlab_header(path: Path) -> None:
    description = (
        f"MATLAB 7.3 MAT-file, Platform: CPython "
        f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}, "
        f"Created on: {datetime.now().strftime('%a %b %d %H:%M:%S %Y')} "
        "HDF5 schema 1.00 ."
    ).encode("ascii")
    header = description[:116].ljust(116, b" ")
    header += bytes.fromhex("00000000 00000000 0002494D")
    with path.open("r+b") as stream:
        stream.write(header)


def _mark_matlab_numeric(dataset: h5py.Dataset, matlab_class: str) -> None:
    dataset.attrs["MATLAB_class"] = np.bytes_(matlab_class)


def _write_matlab_struct(
    handle: h5py.File, name: str, values: dict[str, float | int]
) -> None:
    group = handle.create_group(name)
    group.attrs["MATLAB_class"] = np.bytes_("struct")
    field_type = h5py.special_dtype(vlen=np.dtype("S1"))
    fields = np.empty((len(values),), dtype=field_type)
    for index, field_name in enumerate(values):
        fields[index] = np.asarray(
            [character.encode("ascii") for character in field_name], dtype="S1"
        )
    group.attrs["MATLAB_fields"] = fields
    for field_name, value in values.items():
        if isinstance(value, (int, np.integer)):
            data = np.asarray([[value]], dtype=np.int64)
            matlab_class = "int64"
        else:
            data = np.asarray([[value]], dtype=np.float64)
            matlab_class = "double"
        field = group.create_dataset(field_name, data=data)
        _mark_matlab_numeric(field, matlab_class)
        field.attrs["H5PATH"] = np.bytes_(f"/{name}")


def load_variables(path: str | Path, names: tuple[str, ...]) -> dict[str, np.ndarray]:
    """Load selected variables from classic or MATLAB v7.3 MAT files."""
    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(path)
    if h5py.is_hdf5(path):
        result: dict[str, np.ndarray] = {}
        with h5py.File(path, "r") as handle:
            for name in names:
                if name not in handle:
                    raise KeyError(f"{path} does not contain {name}")
                result[name] = _decode_matlab_hdf5(handle[name])
        return result
    loaded = loadmat(
        path,
        variable_names=list(names),
        squeeze_me=False,
        struct_as_record=False,
    )
    missing = [name for name in names if name not in loaded]
    if missing:
        raise KeyError(f"{path} does not contain: {', '.join(missing)}")
    return {name: np.asarray(loaded[name]) for name in names}


def write_result_metadata(path: str | Path, metadata: dict) -> None:
    Path(path).write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


class FusionResultWriter:
    """Incrementally write a large fused cube without holding it in RAM."""

    def __init__(
        self,
        path: str | Path,
        shape: tuple[int, int, int],
        spectral_response: np.ndarray,
        spatial_response: np.ndarray,
        scale_info: dict[str, float | int],
        block_rows: int,
    ) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with h5py.File(self.path, "w", userblock_size=512):
            pass
        _write_matlab_header(self.path)
        self.handle = h5py.File(self.path, "r+")
        height, width, bands = shape
        self.dataset = self.handle.create_dataset(
            "FusedHSI",
            shape=(bands, width, height),
            dtype=np.float32,
            chunks=(min(16, bands), min(128, width), min(block_rows, height)),
            compression="gzip",
            compression_opts=1,
            shuffle=True,
        )
        _mark_matlab_numeric(self.dataset, "single")
        spectral = self.handle.create_dataset("R_est", data=spectral_response.T)
        _mark_matlab_numeric(spectral, "double")
        spatial = self.handle.create_dataset("B_est", data=spatial_response.T)
        _mark_matlab_numeric(spatial, "double")
        _write_matlab_struct(self.handle, "scaleInfo", scale_info)

    def write_rows(self, first: int, last: int, block: np.ndarray) -> None:
        stored = np.asarray(block, dtype=np.float32).transpose(2, 1, 0)
        self.dataset[:, :, first:last] = stored

    def close(self) -> None:
        self.handle.flush()
        self.handle.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()
