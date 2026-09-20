#!/usr/bin/env python3
"""
Small GT labeling tool for HTD-Mamba MAT datasets.

Persistent output:
  - map: uint8 label map, same H x W as the hyperspectral cube
  - dN: mean spectrum for the current class, e.g. d1 / d2 / d12

No PNG or auxiliary result files are written.
"""

from __future__ import annotations

import argparse
import os
import re
from pathlib import Path

try:
    import matplotlib.pyplot as plt
    from matplotlib import font_manager
    from matplotlib.font_manager import FontProperties
    from matplotlib.widgets import Button
    import numpy as np
    import scipy.io as sio
except ModuleNotFoundError as exc:
    raise SystemExit(
        "Missing dependency: %s. Activate the project environment first:\n"
        "  conda activate htd-mamba\n"
        "or install dependencies in the current environment:\n"
        "  pip install matplotlib numpy scipy"
        % exc.name
    ) from exc


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATASET_PATH = PROJECT_ROOT / "datasets" / "D_20251023_132740.mat"
TARGET_CLASS = "d1"
DEFAULT_BANDS = (67, 45, 120)
CUBE_KEYS = ("X", "hyperspectral_data", "data")
CHINESE_FONT_CANDIDATES = (
    "Microsoft YaHei",
    "SimHei",
    "PingFang SC",
    "Heiti SC",
    "STHeiti",
    "Arial Unicode MS",
    "Noto Sans CJK SC",
)


def configure_fonts():
    available = {font.name for font in font_manager.fontManager.ttflist}
    for name in CHINESE_FONT_CANDIDATES:
        if name in available:
            plt.rcParams["font.sans-serif"] = [name, "DejaVu Sans"]
            plt.rcParams["axes.unicode_minus"] = False
            return FontProperties(family=name)
    return None


def parse_args():
    parser = argparse.ArgumentParser(
        description=(
            "Label GT pixels into a MAT variable named map and save the "
            "current class prior spectrum as dN."
        )
    )
    parser.add_argument(
        "args",
        nargs="*",
        help="Preferred: <dataset.mat> <dN>. Legacy: <dN> with --mat <dataset.mat>.",
    )
    parser.add_argument("--dataset", type=Path, default=None)
    parser.add_argument("--class", dest="class_arg", default=None)
    parser.add_argument("--mat", type=Path, default=None)
    parser.add_argument(
        "--bands",
        default=",".join(str(band) for band in DEFAULT_BANDS),
        help="RGB bands, 1-based, e.g. 67,45,20.",
    )
    return parser.parse_args()


def resolve_cli_args(args):
    if args.dataset is not None or args.class_arg is not None:
        if args.args or args.mat is not None:
            raise SystemExit("Use either positional args or --dataset/--class, not both.")
        return args.dataset or Path(DATASET_PATH), args.class_arg or TARGET_CLASS

    if len(args.args) == 0:
        return args.mat or Path(DATASET_PATH), TARGET_CLASS
    if len(args.args) == 1:
        return args.mat or Path(DATASET_PATH), args.args[0]
    if len(args.args) == 2:
        if args.mat is not None:
            raise SystemExit("Use either '<dataset.mat> <dN>' or '<dN> --mat <dataset.mat>', not both.")
        return Path(args.args[0]), args.args[1]
    raise SystemExit("Usage: python gt_label_tool.py [<dataset.mat> <dN>]")


def parse_class_id(class_arg: str) -> int:
    match = re.fullmatch(r"d(\d+)", class_arg.strip(), flags=re.I)
    if not match:
        raise SystemExit("class must look like d1, d2, d12 ...")
    class_id = int(match.group(1))
    if not (1 <= class_id <= 255):
        raise SystemExit("class id must be between 1 and 255 because map is uint8.")
    return class_id


def parse_bands(raw: str) -> tuple[int, int, int]:
    bands = tuple(int(x.strip()) for x in raw.split(","))
    if len(bands) != 3:
        raise SystemExit("--bands must contain exactly three comma-separated numbers.")
    return bands


def find_cube_key(mat: dict) -> str:
    for key in CUBE_KEYS:
        if key in mat:
            return key
    raise KeyError(
        "MAT file does not contain a hyperspectral cube. "
        "Expected one of: %s." % ", ".join(CUBE_KEYS)
    )


def load_dataset(mat_path: Path):
    mat = sio.loadmat(mat_path)
    cube_key = find_cube_key(mat)
    cube = np.asarray(mat[cube_key])
    if cube.ndim != 3:
        raise ValueError("%s should be a 3-D cube, got shape %s." % (cube_key, cube.shape))

    rows, cols, _bands = cube.shape
    if "map" in mat:
        label_map = np.asarray(mat["map"])
        if label_map.shape != (rows, cols):
            raise ValueError("map shape %s does not match cube shape %s." % (label_map.shape, (rows, cols)))
        label_map = label_map.astype(np.uint8, copy=True)
    else:
        label_map = np.zeros((rows, cols), dtype=np.uint8)

    return mat, cube_key, cube, label_map


def scale_channel(channel: np.ndarray) -> np.ndarray:
    channel = np.asarray(channel, dtype=np.float32)
    finite = channel[np.isfinite(channel)]
    if finite.size == 0:
        return np.zeros(channel.shape, dtype=np.float32)
    lo, hi = np.percentile(finite, [2, 98])
    if not np.isfinite(lo) or not np.isfinite(hi) or hi <= lo:
        lo, hi = float(np.nanmin(finite)), float(np.nanmax(finite))
    if hi <= lo:
        hi = lo + 1.0
    return np.clip((channel - lo) / (hi - lo), 0, 1)


def make_rgb(cube: np.ndarray, bands: tuple[int, int, int]) -> np.ndarray:
    band_count = cube.shape[2]
    channels = []
    for band in bands:
        if band < 1 or band > band_count:
            raise ValueError("Band %d is outside 1..%d." % (band, band_count))
        channels.append(scale_channel(cube[:, :, band - 1]))
    return np.dstack(channels)


def compute_prior(cube: np.ndarray, label_map: np.ndarray, class_id: int):
    mask = label_map == class_id
    if not np.any(mask):
        return None
    spectrum = cube[mask].mean(axis=0, dtype=np.float64).astype(np.float32)
    return spectrum.reshape((-1, 1))


def save_dataset(mat_path: Path, mat: dict, cube: np.ndarray, label_map: np.ndarray, class_id: int):
    out = {key: value for key, value in mat.items() if not key.startswith("__")}
    out["map"] = label_map.astype(np.uint8, copy=False)

    prior_key = "d%d" % class_id
    prior = compute_prior(cube, label_map, class_id)
    if prior is None:
        out.pop(prior_key, None)
        print("No pixels labelled as %s; saved map only and removed stale %s if present." % (prior_key, prior_key))
    else:
        out[prior_key] = prior
        print("Saved %s from %d labelled pixels." % (prior_key, int(np.count_nonzero(label_map == class_id))))

    tmp_path = mat_path.with_suffix(mat_path.suffix + ".tmp")
    try:
        sio.savemat(tmp_path, out, do_compression=False)
        os.replace(tmp_path, mat_path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()


class LabelTool:
    def __init__(self, mat_path: Path, mat: dict, cube_key: str, cube: np.ndarray,
                 label_map: np.ndarray, class_id: int, bands: tuple[int, int, int]):
        self.font_prop = configure_fonts()
        self.mat_path = mat_path
        self.mat = mat
        self.cube_key = cube_key
        self.cube = cube
        self.label_map = label_map
        self.saved_map = label_map.copy()
        self.class_id = class_id
        self.bands = bands
        self.undo_stack = []
        self.drag_button = None

        self.fig, self.ax = plt.subplots(figsize=(11, 8.5))
        self.fig.subplots_adjust(left=0.03, right=0.98, top=0.92, bottom=0.20)
        self.ax.imshow(make_rgb(cube, bands))
        self.current_marks = None
        self.other_marks = None
        self.ax.set_axis_off()
        self.status = self.fig.text(
            0.03,
            0.085,
            "",
            fontsize=10,
            va="top",
            fontproperties=self.font_prop,
        )

        save_ax = self.fig.add_axes([0.80, 0.055, 0.14, 0.055])
        undo_ax = self.fig.add_axes([0.68, 0.055, 0.09, 0.055])
        self.save_button = Button(save_ax, "保存并关闭")
        self.undo_button = Button(undo_ax, "撤销")
        for button in (self.save_button, self.undo_button):
            button.label.set_fontproperties(self.font_prop)
        self.save_button.on_clicked(lambda _event: self.save())
        self.undo_button.on_clicked(lambda _event: self.undo())

        self.fig.canvas.mpl_connect("button_press_event", self.on_press)
        self.fig.canvas.mpl_connect("motion_notify_event", self.on_motion)
        self.fig.canvas.mpl_connect("button_release_event", self.on_release)
        self.fig.canvas.mpl_connect("key_press_event", self.on_key)
        self.update_marks()

    def toolbar_active(self) -> bool:
        toolbar = getattr(self.fig.canvas, "toolbar", None)
        mode = getattr(toolbar, "mode", "") if toolbar is not None else ""
        return bool(str(mode))

    def set_pixel_from_event(self, event):
        if event.inaxes != self.ax or event.xdata is None or event.ydata is None:
            return
        x = int(round(event.xdata))
        y = int(round(event.ydata))
        if not (0 <= y < self.label_map.shape[0] and 0 <= x < self.label_map.shape[1]):
            return
        new_value = self.class_id if self.drag_button == 1 else 0
        old_value = int(self.label_map[y, x])
        if old_value == new_value:
            return
        self.undo_stack.append((y, x, old_value))
        self.label_map[y, x] = new_value
        self.update_marks()

    def on_press(self, event):
        if self.toolbar_active():
            self.drag_button = None
            return
        if event.button not in (1, 3):
            return
        self.drag_button = event.button
        self.set_pixel_from_event(event)

    def on_motion(self, event):
        if self.drag_button in (1, 3):
            self.set_pixel_from_event(event)

    def on_release(self, _event):
        self.drag_button = None

    def on_key(self, event):
        if event.key == "s":
            self.save()
        elif event.key == "u":
            self.undo()
        elif event.key == "q":
            plt.close(self.fig)

    def undo(self):
        if not self.undo_stack:
            return
        y, x, old_value = self.undo_stack.pop()
        self.label_map[y, x] = old_value
        self.update_marks()

    def save(self):
        save_dataset(self.mat_path, self.mat, self.cube, self.label_map, self.class_id)
        self.saved_map = self.label_map.copy()
        self.refresh_status(extra="已保存")
        self.fig.canvas.draw_idle()
        plt.close(self.fig)

    def update_marks(self):
        if self.current_marks is not None:
            self.current_marks.remove()
            self.current_marks = None
        if self.other_marks is not None:
            self.other_marks.remove()
            self.other_marks = None

        current_y, current_x = np.nonzero(self.label_map == self.class_id)
        other_y, other_x = np.nonzero((self.label_map != 0) & (self.label_map != self.class_id))

        if other_x.size:
            self.other_marks = self.ax.scatter(
                other_x,
                other_y,
                marker="o",
                s=26,
                facecolors="none",
                edgecolors="#ffd400",
                linewidths=1.4,
            )
        if current_x.size:
            self.current_marks = self.ax.scatter(
                current_x,
                current_y,
                marker="x",
                s=50,
                c="#ff1f1f",
                linewidths=2.0,
            )
        self.refresh_status()
        self.fig.canvas.draw_idle()

    def refresh_status(self, extra=""):
        current_count = int(np.count_nonzero(self.label_map == self.class_id))
        total_count = int(np.count_nonzero(self.label_map))
        dirty = int(np.count_nonzero(self.label_map != self.saved_map))
        title = (
            "%s | 数据=%s %s | 当前类别=d%d | RGB波段=%s"
            % (self.mat_path.name, self.cube_key, self.cube.shape, self.class_id, self.bands)
        )
        self.ax.set_title(title, fontproperties=self.font_prop)
        text = (
            "左键/拖动：标记当前类别    右键/拖动：取消标记    s：保存并关闭    u：撤销    q：关闭\n"
            "当前类别像素=%d    所有已标像素=%d    未保存修改=%d"
            % (current_count, total_count, dirty)
        )
        if extra:
            text += "    " + extra
        self.status.set_text(text)

    def show(self):
        plt.show()


def main():
    args = parse_args()
    mat_path, class_arg = resolve_cli_args(args)
    mat_path = Path(mat_path).expanduser()
    class_id = parse_class_id(class_arg)
    bands = parse_bands(args.bands)

    if not mat_path.exists():
        raise SystemExit("MAT file not found: %s" % mat_path)

    mat, cube_key, cube, label_map = load_dataset(mat_path)
    print("MAT: %s" % mat_path.resolve())
    print("Cube variable: %s %s" % (cube_key, cube.shape))
    print("Current class: d%d" % class_id)
    print("RGB bands: %s" % (bands,))
    print("Persistent output: map and d%d inside the MAT file." % class_id)

    LabelTool(mat_path, mat, cube_key, cube, label_map, class_id, bands).show()


if __name__ == "__main__":
    main()
