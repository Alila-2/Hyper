import argparse
import base64
import io
import json
import os
import random
import re
import sys
import time
from pathlib import Path

import numpy as np
import scipy.io as sio

try:
    from PIL import Image
except Exception:
    Image = None


COMMON_CUBE_KEYS = [
    "hyperspectral_data",
    "HRHSI",
    "LRHSI",
    "HSI",
    "hsi",
    "data",
    "image",
    "hyperspectral",
    "cube",
    "msi",
    "HRMSI",
]

TARGET_NAMES = {
    "d1": "假草皮",
    "d2": "木板",
    "d3": "竹材",
    "d4": "棕色迷彩服",
    "d5": "绿色迷彩服",
    "d6": "绿色伪装服",
    "d7": "棕色伪装服",
    "d8": "伪装网",
    "d9": "火炮",
    "d10": "坦克",
    "d11": "装甲车",
    "d12": "哨塔",
    "d13": "碉堡",
    "d14": "竹材",
    "d15": "假草皮",
    "d16": "棕色迷彩服",
}

TARGET_CONFIG = {
    "d1": {"threshold": 0.50, "color": [0, 128, 0]},
    "d2": {"threshold": 0.55, "color": [218, 165, 32]},
    "d3": {"threshold": 0.60, "color": [128, 128, 128]},
    "d4": {"threshold": 0.45, "color": [139, 69, 19]},
    "d5": {"threshold": 0.50, "color": [0, 0, 255]},
    "d6": {"threshold": 0.60, "color": [173, 216, 230]},
    "d7": {"threshold": 0.50, "color": [240, 230, 140]},
    "d8": {"threshold": 0.50, "color": [128, 0, 128]},
    "d9": {"threshold": 0.63, "color": [255, 0, 0]},
    "d10": {"threshold": 0.63, "color": [0, 255, 255]},
    "d11": {"threshold": 0.55, "color": [255, 255, 0]},
    "d12": {"threshold": 0.60, "color": [255, 174, 201]},
    "d13": {"threshold": 0.55, "color": [160, 82, 45]},
    "d14": {"threshold": 0.30, "color": [128, 128, 128]},
    "d15": {"threshold": 0.70, "color": [0, 128, 0]},
    "d16": {"threshold": 0.65, "color": [139, 69, 19]},
}

HTD_MAMBA_MODELS = {
    "joint": {"band": 320, "m": 15, "checkpoint": "checkpoints/joint_ckpt.pt"},
    "Sandiego": {"band": 189, "m": 30, "checkpoint": "models/Sandiego/ckpt_159_.pt"},
    "Sandiego2": {"band": 189, "m": 5, "checkpoint": "models/Sandiego2/ckpt_12_.pt"},
    "abu-airport-2": {"band": 205, "m": 5, "checkpoint": "models/abu-airport-2/ckpt_187_.pt"},
    "abu-beach-4": {"band": 102, "m": 15, "checkpoint": "models/abu-beach-4/ckpt_180_.pt"},
}


def standard(values):
    values = np.asarray(values, dtype=np.float32)
    finite = np.isfinite(values)
    if not np.any(finite):
        return np.zeros_like(values, dtype=np.float32)
    lo = float(np.min(values[finite]))
    hi = float(np.max(values[finite]))
    if hi <= lo:
        return np.zeros_like(values, dtype=np.float32)
    out = (values - lo) / (hi - lo)
    return np.clip(out, 0.0, 1.0).astype(np.float32, copy=False)


def postprocess_detection(raw_similarity, height, width, delta, gamma=10.0):
    initial_map = np.exp(-1.0 * (raw_similarity - 1.0) ** 2 / delta)
    initial_map = np.reshape(initial_map, (height, width), order="F")
    initial_map = standard(initial_map)

    refined_map = np.exp(-float(gamma) * (1.0 - initial_map) ** 2)
    return standard(refined_map)


def load_uploaded_data(input_path, preview_only=False):
    suffix = Path(input_path).suffix.lower()
    if suffix == ".mat":
        variable_names = None
        if preview_only:
            variable_names = [select_cube_variable_name(input_path)]
        data = sio.loadmat(input_path, variable_names=variable_names)
        cube = select_cube(data)
        data["hyperspectral_data"] = cube.astype(np.float32, copy=False)
        return data
    if suffix in [".png", ".jpg", ".jpeg"]:
        if Image is None:
            raise RuntimeError("Pillow is required to run detection on image uploads")
        image = Image.open(input_path).convert("RGB")
        cube = np.asarray(image, dtype=np.float32)
        return {"hyperspectral_data": cube}
    raise RuntimeError(f"Unsupported detection input file: {suffix}")


def select_cube_variable_name(input_path):
    variables = sio.whosmat(input_path)
    by_name = {name: shape for name, shape, _ in variables}
    for key in COMMON_CUBE_KEYS:
        shape = by_name.get(key)
        if shape and len(shape) == 3 and shape[2] > 1:
            return key
    for name, shape, _ in variables:
        if len(shape) == 3 and shape[2] > 1:
            return name
    raise RuntimeError("No hyperspectral cube variable found in MAT file")


def select_cube(data):
    for key in COMMON_CUBE_KEYS:
        value = data.get(key)
        if is_cube(value):
            return np.asarray(value, dtype=np.float32)
    for key, value in data.items():
        if not key.startswith("__") and is_cube(value):
            return np.asarray(value, dtype=np.float32)
    raise RuntimeError("No hyperspectral cube variable found in MAT file")


def is_cube(value):
    return isinstance(value, np.ndarray) and value.ndim == 3 and value.shape[2] > 1


def parse_targets(targets_text, data):
    if not targets_text:
        targets = list(TARGET_CONFIG.keys())
    else:
        try:
            parsed = json.loads(targets_text)
        except json.JSONDecodeError:
            parsed = [
                part.strip().strip("\"'")
                for part in re.split(r"[,;]", targets_text.strip().strip("[]"))
                if part.strip()
            ]
        if isinstance(parsed, dict):
            targets = [key for key, enabled in parsed.items() if enabled]
        else:
            targets = list(parsed)
    targets = [target for target in targets if target in TARGET_CONFIG]
    if not targets:
        targets = list(TARGET_CONFIG.keys())
    return targets


def load_target_library(target_mat_path):
    if not target_mat_path:
        return {}
    path = Path(target_mat_path)
    if not path.exists():
        return {}
    return sio.loadmat(path)


def resolve_target_spectrum(data, target_library, target_type, bands, algorithm):
    if target_type in data:
        spectrum = np.asarray(data[target_type]).reshape(-1).astype(np.float32)
    elif target_type in target_library:
        spectrum = np.asarray(target_library[target_type]).reshape(-1).astype(np.float32)
        if spectrum.shape[0] == 320 and bands == 64:
            spectrum = spectrum[::5]
    else:
        return None
    if spectrum.size < 2:
        return None
    if algorithm != "htd-mamba" and spectrum.shape[0] != bands:
        return None
    return spectrum


def cube_to_rgb(cube):
    bands = cube.shape[2]
    indices = [66, 44, 19] if bands >= 320 else [13, 9, 5]
    indices = [min(max(index, 0), bands - 1) for index in indices]
    rgb = np.empty((cube.shape[0], cube.shape[1], 3), dtype=np.float32)
    for channel, index in enumerate(indices):
        values = np.asarray(cube[:, :, index], dtype=np.float32)
        finite = np.isfinite(values)
        if not np.any(finite):
            rgb[:, :, channel] = 0.0
            continue
        lo, hi = np.percentile(values[finite], [2.0, 98.0])
        if hi <= lo:
            rgb[:, :, channel] = 0.0
            continue
        normalized = (values - lo) / (hi - lo)
        rgb[:, :, channel] = np.clip(normalized, 0.0, 1.0)
    return rgb


def hcem_detection_map(cube, spectrum, target_from_library):
    height, width, bands = cube.shape
    x_matrix = cube.transpose(2, 0, 1).reshape(bands, -1).astype(np.float32, copy=False)
    pixels = height * width
    lambda_value = 50.0
    epsilon = 1e-6
    weights = np.ones(pixels, dtype=np.float32)
    y_old = np.ones(pixels, dtype=np.float32)
    epoch = 3 if target_from_library else 5

    identity = np.eye(bands, dtype=np.float32)
    spectrum = spectrum.astype(np.float32, copy=False)
    for _ in range(epoch):
        weighted = x_matrix * weights
        covariance = weighted @ weighted.T / pixels
        inv_covariance = np.linalg.inv(covariance + 0.0001 * identity)
        denominator = float(spectrum.T @ inv_covariance @ spectrum)
        if abs(denominator) < 1e-12:
            return np.zeros((height, width), dtype=np.float32)
        detector = inv_covariance @ spectrum / denominator
        y_values = detector @ weighted
        weights = np.maximum(1.0 - np.exp(-lambda_value * y_values), 0.0).astype(np.float32)
        if abs(np.linalg.norm(y_old) ** 2 / pixels - np.linalg.norm(y_values) ** 2 / pixels) < epsilon:
            break
        y_old = y_values.copy()
    return standard(y_values.reshape(height, width))


def htd_mamba_detection_maps(cube, target_spectra, model_root, model_dataset,
                              requested_device, batch_size, delta=0.1):
    if model_dataset not in HTD_MAMBA_MODELS:
        raise RuntimeError(
            "Unsupported HTD-Mamba model dataset: %s. Available values: %s"
            % (model_dataset, ", ".join(HTD_MAMBA_MODELS.keys()))
        )
    if batch_size <= 0:
        raise RuntimeError("HTD-Mamba batch size must be greater than zero")

    root = Path(model_root).resolve()
    if not root.is_dir():
        raise RuntimeError("HTD-Mamba root directory not found: %s" % root)
    root_text = str(root)
    if root_text not in sys.path:
        sys.path.insert(0, root_text)

    try:
        import torch
        import torch.nn.functional as torch_functional
        from CL_Model import SpectralGroupAttention
        from PyramidSSM import print_backend_status
    except Exception as exception:
        raise RuntimeError("Unable to import HTD-Mamba runtime: %s" % exception) from exception

    if requested_device == "auto":
        device_text = "cuda:0" if torch.cuda.is_available() else "cpu"
    else:
        device_text = requested_device
    if device_text.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("HTD-Mamba requested CUDA, but CUDA is unavailable")
    device = torch.device(device_text)

    model_config = HTD_MAMBA_MODELS[model_dataset]
    model_band = int(model_config["band"])
    checkpoint = root / model_config["checkpoint"]
    if not checkpoint.is_file():
        raise RuntimeError("HTD-Mamba checkpoint not found: %s" % checkpoint)

    model = SpectralGroupAttention(
        band=model_band,
        group_length=int(model_config["m"]),
        channel_dim=16,
        state_size=16,
        device=device,
        layer=1,
    ).to(device)
    state_dict = torch.load(str(checkpoint), map_location=device)
    if isinstance(state_dict, dict) and "state_dict" in state_dict:
        state_dict = state_dict["state_dict"]
    model.load_state_dict(state_dict)
    model.eval()
    print_backend_status(device_text)

    height, width, input_bands = cube.shape
    pixels = height * width
    flat_cube = np.reshape(
        np.asarray(cube, dtype=np.float32), (pixels, input_bands), order="F"
    )
    finite_cube = flat_cube[np.isfinite(flat_cube)]
    if finite_cube.size == 0:
        raise RuntimeError("HTD-Mamba input cube contains no finite values")
    cube_min = float(np.min(finite_cube))
    cube_max = float(np.max(finite_cube))
    cube_range = cube_max - cube_min
    if cube_range <= 0.0:
        raise RuntimeError("HTD-Mamba input cube has a constant value")

    target_codes = list(target_spectra.keys())
    target_matrix = np.stack([target_spectra[code] for code in target_codes]).astype(np.float32)
    target_matrix = np.nan_to_num(target_matrix, nan=cube_min, posinf=cube_max, neginf=cube_min)
    target_matrix = np.clip((target_matrix - cube_min) / cube_range, 0.0, 1.0)

    def prepare_tensor(values):
        tensor = torch.from_numpy(values).to(device=device, dtype=torch.float32).unsqueeze(1)
        if tensor.shape[-1] != model_band:
            tensor = torch_functional.interpolate(
                tensor, size=model_band, mode="linear", align_corners=True
            )
        return tensor

    score_matrix = np.empty((len(target_codes), pixels), dtype=np.float32)
    with torch.inference_mode():
        target_features = model(prepare_tensor(target_matrix))
        target_features = torch_functional.normalize(target_features, dim=-1)
        for start in range(0, pixels, batch_size):
            end = min(start + batch_size, pixels)
            pixel_batch = np.nan_to_num(
                flat_cube[start:end], nan=cube_min, posinf=cube_max, neginf=cube_min
            )
            pixel_batch = np.clip((pixel_batch - cube_min) / cube_range, 0.0, 1.0)
            pixel_features = model(prepare_tensor(pixel_batch.astype(np.float32, copy=False)))
            pixel_features = torch_functional.normalize(pixel_features, dim=-1)
            similarity = pixel_features @ target_features.transpose(0, 1)
            score_matrix[:, start:end] = similarity.transpose(0, 1).cpu().numpy()

    detection_maps = {}
    for index, target_code in enumerate(target_codes):
        detection_maps[target_code] = postprocess_detection(
            score_matrix[index],
            height,
            width,
            delta,
            gamma=10.0,
        )
    return detection_maps


def empty_statistics(config):
    return {
        "detected_pixels": 0,
        "object_count": 0,
        "objects": [],
        "max_confidence": 0.0,
        "detection_threshold": float(config["threshold"]),
        "is_detected": False,
        "mean_confidence": 0.0,
        "coverage_percentage": 0.0,
    }


def analyze_and_merge(detection_results, height, width):
    from scipy import ndimage

    fused_image = np.zeros((height, width, 3), dtype=np.float32)
    detected_targets = []
    target_statistics = {}
    min_detection_pixels = 3
    min_object_size = 5

    for result in detection_results:
        target_type = result["type"]
        config = TARGET_CONFIG[target_type]
        threshold = float(config["threshold"])
        detection_map = np.asarray(result["detection_map"], dtype=np.float32)
        high_mask = detection_map >= threshold
        high_confidence_map = np.where(high_mask, detection_map, 0.0).astype(np.float32, copy=False)
        detected_pixels = int(np.sum(high_mask))
        max_value = float(np.max(high_confidence_map)) if detected_pixels > 0 else 0.0
        object_info = []

        if detected_pixels >= min_detection_pixels and max_value > threshold:
            labeled_array, num_features = ndimage.label(high_mask.astype(np.int32, copy=False))
            if num_features > 0:
                sizes = np.bincount(labeled_array.ravel())
                labels = np.arange(1, num_features + 1, dtype=np.int32)
                valid_idx = np.flatnonzero(sizes[1:] >= min_object_size)
                if valid_idx.size > 0:
                    valid_labels = labels[valid_idx]
                    valid_sizes = sizes[1:][valid_idx]
                    centers = ndimage.center_of_mass(high_mask, labels=labeled_array, index=valid_labels)
                    mean_scores = ndimage.mean(detection_map, labels=labeled_array, index=valid_labels)
                    max_scores = ndimage.maximum(detection_map, labels=labeled_array, index=valid_labels)
                    slices = ndimage.find_objects(labeled_array)
                    for idx, label in enumerate(valid_labels):
                        y_slice, x_slice = slices[label - 1]
                        object_info.append({
                            "id": int(label),
                            "size": int(valid_sizes[idx]),
                            "bbox": [
                                int(x_slice.start),
                                int(y_slice.start),
                                int(x_slice.stop - 1),
                                int(y_slice.stop - 1),
                            ],
                            "center": [round(float(centers[idx][1]), 4), round(float(centers[idx][0]), 4)],
                            "max_confidence": round(float(max_scores[idx]), 4),
                            "mean_confidence": round(float(mean_scores[idx]), 4),
                        })

        is_detected = detected_pixels >= min_detection_pixels and max_value > threshold and len(object_info) > 0
        mean_confidence = float(np.mean(high_confidence_map[high_confidence_map > 0])) if detected_pixels > 0 else 0.0
        target_statistics[target_type] = {
            "detected_pixels": detected_pixels,
            "object_count": len(object_info),
            "objects": object_info,
            "max_confidence": round(max_value, 4),
            "detection_threshold": threshold,
            "is_detected": bool(is_detected),
            "mean_confidence": round(mean_confidence, 4),
            "coverage_percentage": round(float(detected_pixels * 100.0 / max(1, height * width)), 4),
        }

        if is_detected:
            color = np.asarray(config["color"], dtype=np.float32) / 255.0
            fused_image = np.maximum(fused_image, high_confidence_map[..., None] * color[None, None, :])
            detected_targets.append({
                "type": target_type,
                "name": TARGET_NAMES.get(target_type, target_type),
                "color": config["color"],
                "color_hex": "#{:02X}{:02X}{:02X}".format(*config["color"]),
                "pixel_count": detected_pixels,
                "object_count": len(object_info),
                "max_value": round(max_value, 4),
            })

    return np.clip(fused_image, 0.0, 1.0), detected_targets, target_statistics


def write_png_base64(image, output_path):
    if Image is None:
        raise RuntimeError("Pillow is required to encode detection preview images")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    rgb = np.asarray(image, dtype=np.float32)
    rgb = np.nan_to_num(rgb, nan=0.0, posinf=1.0, neginf=0.0)
    rgb = (np.clip(rgb, 0.0, 1.0) * 255.0).astype(np.uint8)
    alpha = np.full(rgb.shape[:2] + (1,), 255, dtype=np.uint8)
    rgba = np.concatenate((rgb, alpha), axis=2)
    buffer = io.BytesIO()
    Image.fromarray(rgba, mode="RGBA").save(buffer, format="PNG", compress_level=1)
    png_data = buffer.getvalue()
    output_path.write_bytes(png_data)
    return base64.b64encode(png_data).decode("ascii")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--algorithm", choices=["hcem", "htd-mamba"], default="htd-mamba")
    parser.add_argument("--input", required=True)
    parser.add_argument("--targets", default="[]")
    parser.add_argument("--output-json", required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--target-spectrum-mat", default="")
    parser.add_argument("--preview-only", action="store_true")
    parser.add_argument("--random-delay-min-seconds", type=int, default=0)
    parser.add_argument("--random-delay-max-seconds", type=int, default=0)
    parser.add_argument("--htd-mamba-root", default="")
    parser.add_argument("--htd-mamba-model-dataset", default="joint")
    parser.add_argument("--htd-mamba-device", default="auto")
    parser.add_argument("--htd-mamba-batch-size", type=int, default=128)
    args = parser.parse_args()

    started_at = time.perf_counter()
    work_dir = Path(args.work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    data = load_uploaded_data(args.input, preview_only=args.preview_only)
    cube = data["hyperspectral_data"].astype(np.float32, copy=False)
    height, width, bands = cube.shape
    if args.preview_only:
        image_path = work_dir / ("detection_preview_" + str(int(time.time() * 1000)) + ".png")
        result = {
            "success": True,
            "image": write_png_base64(cube_to_rgb(cube), image_path),
            "message": "success",
            "file_type": Path(args.input).suffix.lower(),
            "shape": [int(height), int(width), int(bands)],
        }
        output_json = Path(args.output_json)
        output_json.parent.mkdir(parents=True, exist_ok=True)
        output_json.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
        return

    if args.random_delay_min_seconds < 0:
        raise RuntimeError("Random detection delay minimum must not be negative")
    if args.random_delay_max_seconds < args.random_delay_min_seconds:
        raise RuntimeError("Random detection delay maximum must be greater than or equal to the minimum")
    delay_seconds = random.randint(
        args.random_delay_min_seconds,
        args.random_delay_max_seconds,
    )
    if delay_seconds > 0:
        time.sleep(delay_seconds)

    target_library = load_target_library(args.target_spectrum_mat)
    target_codes = parse_targets(args.targets, data)

    resolved_targets = {}
    target_sources = {}
    missing_targets = []
    for target_type in target_codes:
        spectrum = resolve_target_spectrum(
            data, target_library, target_type, bands, args.algorithm
        )
        if spectrum is None:
            missing_targets.append(target_type)
            continue
        resolved_targets[target_type] = spectrum
        target_sources[target_type] = "mat" if target_type in data else "library"

    if not resolved_targets:
        runtime = round(time.perf_counter() - started_at, 3)
        result = {
            "success": False,
            "image": "",
            "message": "\u672a\u627e\u5230\u4e0e\u8f93\u5165\u6ce2\u6bb5\u6570\u517c\u5bb9\u7684\u76ee\u6807\u5148\u9a8c\u5149\u8c31",
            "detected_targets": [],
            "target_statistics": {},
            "cached": False,
            "runtime": runtime,
            "algorithm": args.algorithm,
            "wzml": 0,
            "jsml": 0,
            "pyl": None,
            "jsjd": None,
            "missing_targets": missing_targets,
        }
        output_json = Path(args.output_json)
        output_json.parent.mkdir(parents=True, exist_ok=True)
        output_json.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
        return

    detection_results = []
    if args.algorithm == "htd-mamba" and resolved_targets:
        detection_maps = htd_mamba_detection_maps(
            cube,
            resolved_targets,
            args.htd_mamba_root,
            args.htd_mamba_model_dataset,
            args.htd_mamba_device,
            args.htd_mamba_batch_size,
        )
        detection_results = [
            {"type": target_type, "detection_map": detection_maps[target_type]}
            for target_type in resolved_targets
        ]
    else:
        for target_type, spectrum in resolved_targets.items():
            detection_map = hcem_detection_map(
                cube, spectrum, target_sources[target_type] == "library"
            )
            detection_results.append({"type": target_type, "detection_map": detection_map})

    fused_image, detected_targets, target_statistics = analyze_and_merge(detection_results, height, width)
    for target_type in missing_targets:
        target_statistics[target_type] = empty_statistics(TARGET_CONFIG[target_type])

    image_path = work_dir / ("detection_" + str(int(time.time() * 1000)) + ".png")
    image_base64 = write_png_base64(fused_image, image_path)
    runtime = round(time.perf_counter() - started_at, 3)
    result = {
        "success": True,
        "image": image_base64,
        "detected_targets": detected_targets,
        "target_statistics": target_statistics,
        "cached": False,
        "runtime": runtime,
        "algorithm": args.algorithm,
        "wzml": 0,
        "jsml": 0,
        "pyl": None,
        "jsjd": None,
        "missing_targets": missing_targets,
    }
    output_json = Path(args.output_json)
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
