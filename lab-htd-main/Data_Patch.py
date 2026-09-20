import torch.utils.data as data
import numpy as np
import torch
from tqdm import tqdm

from Utils import load_cube


def standard(x):
    max_value = np.max(x)
    min_value = np.min(x)
    if max_value == min_value:
        return np.zeros_like(x)
    return np.float32((x - min_value) / (max_value - min_value))


def cosin_similarity(x, y):
    x_norm = np.sqrt(np.sum(x ** 2, axis=1))
    y_norm = np.sqrt(np.sum(y ** 2, axis=1))
    x_y_multi = np.sum(np.multiply(x, y), axis=1)
    return x_y_multi / (x_norm * y_norm + 1e-8)


def patch_encoded(patch, center):
    p_h, p_w, b = patch.shape
    patch_unfold = np.reshape(patch, [-1, b], order='F')
    assert patch_unfold.shape[1] == center.shape[1]
    encoded_weight = cosin_similarity(patch_unfold, center)
    encoded_weight = np.exp(encoded_weight) / np.sum(np.exp(encoded_weight))
    encoded_weight = encoded_weight[:, None]
    encoded_vector = np.sum(encoded_weight * patch_unfold, axis=0)
    encoded_vector = encoded_vector[None, :]
    return encoded_vector


def _softmax_weighted_encode(patch_unfold, center):
    '''
    Vectorised equivalent of `patch_encoded` for a whole batch of patches.

    patch_unfold: (N, P, band) - N patches, each flattened to P pixels of `band` dims
    center:       (N, band)    - the center pixel of each patch

    Returns (N, band): the softmax(cosine-similarity)-weighted sum of each patch,
    numerically identical to calling `patch_encoded` on each patch one by one.
    '''
    patch_norm = np.sqrt(np.sum(patch_unfold ** 2, axis=2))
    center_norm = np.sqrt(np.sum(center ** 2, axis=1, keepdims=True))
    dot = np.sum(patch_unfold * center[:, None, :], axis=2)
    cosine = dot / (patch_norm * center_norm + 1e-8)

    exp_cosine = np.exp(cosine)
    weight = exp_cosine / np.sum(exp_cosine, axis=1, keepdims=True)
    encoded = np.sum(weight[:, :, None] * patch_unfold, axis=1)
    return encoded


class Data(data.Dataset):
    '''
    Precomputes the (center, coded_vector) pair for every pixel once at
    construction time and caches them in CPU memory. The coded_vector only
    depends on the raw image, not on the model weights, so recomputing it every
    epoch (as the original per-item implementation did) is pure waste.

    Memory budget: two float32 arrays of shape (numpixel, band). For a
    500x500x320 cube that is ~640 MB total, which stays in CPU RAM and adds
    zero GPU memory pressure. Precomputation is done in blocks so the transient
    patch tensor never blows up to (numpixel, P, band) at once.
    '''

    def __init__(self, path, w_size=7, precompute_block=4096,
                 max_samples=None, seed=1):
        self.w_size = w_size
        self.pad_size = w_size // 2
        img, _ = load_cube(path)

        self.h, self.w, b = img.shape
        self.total_nums = self.h * self.w
        if max_samples is not None and max_samples < self.total_nums:
            rng = np.random.default_rng(seed)
            self.indices = np.sort(
                rng.choice(self.total_nums, size=max_samples, replace=False)
            )
        else:
            self.indices = np.arange(self.total_nums)
        self.nums = int(self.indices.shape[0])
        img = standard(img)

        padded = np.pad(
            img,
            ((self.pad_size, self.pad_size), (self.pad_size, self.pad_size), (0, 0)),
            mode='reflect',
        )

        self.centers, self.coded_vectors = self._precompute_all(
            padded, b, precompute_block
        )

    def _precompute_all(self, padded, band, block_size):
        '''
        Compute center and coded_vector for all pixels, processing `block_size`
        pixels at a time to keep the transient patch buffer small.
        '''
        window = self.w_size
        centers = np.empty((self.nums, band), dtype=np.float32)
        coded = np.empty((self.nums, band), dtype=np.float32)

        for start in tqdm(
            range(0, self.nums, block_size),
            desc="precompute patches",
            dynamic_ncols=True,
        ):
            end = min(start + block_size, self.nums)
            indices = self.indices[start:end]

            position_y = indices // self.h
            position_x = indices - position_y * self.h
            row = position_x + self.pad_size
            col = position_y + self.pad_size

            n = end - start
            patch_block = np.empty((n, window, window, band), dtype=padded.dtype)
            for k in range(n):
                r = row[k]
                c = col[k]
                patch_block[k] = padded[
                    r - self.pad_size:r + self.pad_size + 1,
                    c - self.pad_size:c + self.pad_size + 1,
                    :,
                ]

            center_block = patch_block[:, self.pad_size, self.pad_size, :]
            patch_unfold = np.reshape(
                patch_block, [n, window * window, band], order='F'
            )
            coded_block = _softmax_weighted_encode(patch_unfold, center_block)

            centers[start:end] = center_block.astype(np.float32)
            coded[start:end] = coded_block.astype(np.float32)

        return centers, coded

    def __getitem__(self, index):
        center = self.centers[index][None, :]
        coded_vector = self.coded_vectors[index][None, :]
        return torch.from_numpy(center).float(), torch.from_numpy(coded_vector).float()

    def __len__(self):
        return self.nums


class JointData(data.Dataset):
    '''
    Joint training dataset for multiple scenes with the same spectral band count.
    Each scene is normalised and patch-encoded independently, then the resulting
    (center, coded_vector) pairs are concatenated for one shared model.
    '''

    def __init__(self, paths, w_size=7, precompute_block=4096,
                 max_samples_per_dataset=None, seed=1):
        centers = []
        coded_vectors = []
        band = None
        self.scene_sizes = []

        for scene_idx, path in enumerate(paths):
            scene = Data(
                path,
                w_size=w_size,
                precompute_block=precompute_block,
                max_samples=max_samples_per_dataset,
                seed=seed + scene_idx,
            )
            if band is None:
                band = scene.centers.shape[1]
            elif scene.centers.shape[1] != band:
                raise ValueError(
                    "All joint-training scenes must have the same band count: "
                    "%s has %d, expected %d"
                    % (path, scene.centers.shape[1], band)
                )
            centers.append(scene.centers)
            coded_vectors.append(scene.coded_vectors)
            self.scene_sizes.append(scene.nums)

        self.centers = np.concatenate(centers, axis=0)
        self.coded_vectors = np.concatenate(coded_vectors, axis=0)
        self.nums = int(self.centers.shape[0])

    def __getitem__(self, index):
        center = self.centers[index][None, :]
        coded_vector = self.coded_vectors[index][None, :]
        return torch.from_numpy(center).float(), torch.from_numpy(coded_vector).float()

    def __len__(self):
        return self.nums
