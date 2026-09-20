import os
import time
from typing import Dict
import torch
import numpy as np
from ts_generation import ts_generation
from Data_Patch import Data, JointData
import matplotlib
matplotlib.use('Agg')  # headless backend: no GUI, safe for background training
import matplotlib.pyplot as plt
from CL_Model import SpectralGroupAttention
import torch.optim as optim
from tqdm import tqdm
from Utils import checkFile, standard, load_cube, standard_with_range
from Scheduler import GradualWarmupScheduler
import torch.nn.functional as F
import scipy.io as sio
from sklearn import metrics
import random
from prior_spectrum import (
    resolve_prior_spectrum,
    resolve_all_priors,
    normalize_prior_to_data,
)


def seed_torch(seed=1):
    '''
    Keep the seed fixed thus the results can keep stable
    '''
    random.seed(seed)
    os.environ['PYTHONHASHSEED'] = str(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed(seed)
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.benchmark = False
        torch.backends.cudnn.deterministic = True


def _save_detection_png(detection_map, save_path):
    '''
    Save a detection heatmap without opening any GUI window.
    '''
    save_dir = os.path.dirname(save_path)
    if save_dir:
        checkFile(save_dir)
    image = standard(np.asarray(detection_map, dtype=np.float64))
    plt.imsave(save_path, image, cmap='hot', vmin=0, vmax=1)
    print('detection png saved to %s' % save_path)


def _save_detection_contact_sheet(detection_maps, save_path):
    '''
    Save one overview PNG for all priors in a multi-prior detection run.
    '''
    if not detection_maps:
        return
    save_dir = os.path.dirname(save_path)
    if save_dir:
        checkFile(save_dir)
    items = sorted(detection_maps.items(), key=lambda item: int(item[0][1:]))
    cols = min(4, len(items))
    rows = int(np.ceil(len(items) / float(cols)))
    fig, axes = plt.subplots(rows, cols, figsize=(4 * cols, 3.6 * rows))
    axes = np.asarray(axes).reshape(-1)
    for ax, (key, detection_map) in zip(axes, items):
        ax.imshow(standard(np.asarray(detection_map, dtype=np.float64)),
                  cmap='hot', vmin=0, vmax=1)
        ax.set_title(key)
        ax.axis('off')
    for ax in axes[len(items):]:
        ax.axis('off')
    fig.tight_layout()
    fig.savefig(save_path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    print('detection overview png saved to %s' % save_path)


def train(modelConfig: Dict):
    seed_torch(modelConfig['seed'])
    device = torch.device(modelConfig["device"])
    # Data precomputes (center, coded_vector) for every pixel once and caches
    # them as two in-memory arrays. We then train by slicing those arrays
    # directly instead of going through a multi-worker DataLoader: the samples
    # are tiny (1 x band vectors) so the DataLoader's per-sample fetch and
    # inter-process communication dominated the epoch time (~88%). Direct
    # slicing removes that overhead entirely.
    train_paths = modelConfig.get("train_paths")
    if train_paths:
        dataset = JointData(
            train_paths,
            w_size=modelConfig['patch_size'],
            max_samples_per_dataset=modelConfig.get(
                "max_train_samples_per_dataset"
            ),
            seed=modelConfig['seed'],
        )
        print(
            "joint train dataset: %d samples from %s"
            % (len(dataset), ", ".join(train_paths))
        )
    else:
        dataset = Data(
            modelConfig["path"],
            w_size=modelConfig['patch_size'],
            max_samples=modelConfig.get("max_train_samples"),
            seed=modelConfig['seed'],
        )
    all_centers = torch.from_numpy(dataset.centers).float()
    all_coded = torch.from_numpy(dataset.coded_vectors).float()
    num_samples = all_centers.shape[0]
    batch_size = modelConfig["batch_size"]
    # model setup
    net_model = SpectralGroupAttention(band=modelConfig['band'], group_length=modelConfig['m'],
                                       channel_dim=modelConfig['channel'], state_size=modelConfig['state_size'],
                                       device=device, layer=modelConfig['layer']).to(device)
    if modelConfig["training_load_weight"] is not None:
        net_model.load_state_dict(torch.load(os.path.join(
            modelConfig["save_dir"], modelConfig["training_load_weight"]), map_location=device), strict=False)
        print("Model weight load down.")
    optimizer = torch.optim.AdamW(
        net_model.parameters(), lr=modelConfig["lr"], weight_decay=1e-4)
    cosineScheduler = optim.lr_scheduler.CosineAnnealingLR(
        optimizer=optimizer, T_max=modelConfig["epoch"], eta_min=0, last_epoch=-1)
    # warm_epoch must be >= 1: with small epoch counts (e.g. epoch<10) the old
    # `epoch // 10` becomes 0 and the warmup scheduler divides by zero.
    warm_epoch = max(1, modelConfig["epoch"] // 10)
    warmUpScheduler = GradualWarmupScheduler(optimizer=optimizer, multiplier=modelConfig["multiplier"],
                                             warm_epoch=warm_epoch, after_scheduler=cosineScheduler)
    path = modelConfig["save_dir"] + '/' + modelConfig['dataset'] + '/'
    checkFile(path)
    net_model.train()
    for e in range(modelConfig["epoch"]):
        perm = torch.randperm(num_samples)
        with tqdm(
            _batch_ranges(num_samples, batch_size),
            total=_num_batches(num_samples, batch_size),
            dynamic_ncols=True,
        ) as tqdmBatches:
            for start_idx, end_idx in tqdmBatches:
                idx = perm[start_idx:end_idx]
                center_pixels = all_centers[idx].unsqueeze(1)
                coded_vectors = all_coded[idx].unsqueeze(1)
                current_batch_size = center_pixels.shape[0]
                if current_batch_size < 2:
                    continue
                combined_vectors = torch.cat([center_pixels, coded_vectors], dim=0)
                optimizer.zero_grad()
                x_0 = combined_vectors.to(device)
                features = net_model(x_0)
                loss = info_nce_loss(features, current_batch_size)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(
                    net_model.parameters(), modelConfig["grad_clip"])
                optimizer.step()
                tqdmBatches.set_postfix(ordered_dict={
                    "epoch": e,
                    "loss: ": loss.item(),
                    "LR": optimizer.state_dict()['param_groups'][0]["lr"]
                })
        warmUpScheduler.step()
        if e % 1 == 0:
            torch.save(net_model.state_dict(), os.path.join(
                path, 'ckpt_' + str(e) + "_.pt"))


def normalize(*xs):
    return [None if x is None else F.normalize(x, dim=-1) for x in xs]


def cosin_similarity(x, y):
    assert x.shape[1] == y.shape[1]
    x_norm = np.sqrt(np.sum(x ** 2, axis=1))
    y_norm = np.sqrt(np.sum(y ** 2, axis=1))
    x_y = np.sum(np.multiply(x, y), axis=1)
    return x_y / (x_norm * y_norm + 1e-8)


def _batch_ranges(total, batch_size):
    for start in range(0, total, batch_size):
        yield start, min(start + batch_size, total)


def _num_batches(total, batch_size):
    return (total + batch_size - 1) // batch_size


def transpose(x):
    return x.transpose(-2, -1)


def info_nce_loss(x, size, temperature=0.1, reduction='mean'):
    batch_x0 = x[:size]
    batch_x1 = x[size:]
    batch_x0, batch_x1 = normalize(batch_x0, batch_x1)
    logits = batch_x0 @ transpose(batch_x1)

    # Positive keys are the entries on the diagonal
    labels = torch.arange(len(batch_x0), device=batch_x0.device)

    return F.cross_entropy(logits / temperature, labels, reduction=reduction)

def _auc_from_maps(gt_binary, detection_map, epision):
    '''Single AUC (ROC) value from a binarised gt and a detection map.'''
    y_l = np.reshape(gt_binary, [-1, 1], order='F')
    y_p = np.reshape(detection_map, [-1, 1], order='F')
    try:
        fpr, tpr, _ = metrics.roc_curve(y_l, y_p, drop_intermediate=False)
        fpr = fpr[1:]
        tpr = tpr[1:]
        return round(metrics.auc(fpr, tpr), epision)
    except Exception:
        return 0.5

def select_best(modelConfig: Dict):
    '''
    Scan every saved checkpoint and pick the epoch with the best AUC.

    Requires a ground-truth map. If the dataset has no map (e.g. detection1 /
    detection2), AUC cannot be computed, so this raises with a clear message
    telling the caller to fall back to the last / loss-selected epoch instead.

    The map may be multi-class (value k marks the pixels of prior dk). In that
    case each available prior dk is scored against (map == k) and the epoch score
    is the mean AUC across all classes, so the chosen checkpoint is good for the
    scene as a whole rather than a single class.
    '''
    seed_torch(modelConfig['seed'])
    device = torch.device(modelConfig["device"])
    path = modelConfig["save_dir"] + '/' + modelConfig['dataset'] + '/'

    data, mat = load_cube(modelConfig["path"])
    if 'map' not in mat:
        raise ValueError(
            "select_best needs a ground-truth 'map' to score AUC, but dataset "
            "'%s' has none. Use the last (or loss-selected) epoch instead."
            % modelConfig['dataset']
        )
    gt_map = np.asarray(mat['map'])
    data, data_min, data_max = standard_with_range(data)
    h, w, c = data.shape
    numpixel = h * w
    data_matrix = np.reshape(data, [-1, c], order='F')

    # Priors to score: dataset-embedded dk only (no wzcl fallback), each mapped
    # into the cube's normalised scale, band-matched to the cube. If the dataset
    # is an original benchmark without embedded priors, fall back to the
    # original target-spectrum generation from the GT map.
    try:
        priors = resolve_all_priors(mat, expected_band=c)
        priors = [
            (key, normalize_prior_to_data(spectrum, data_min, data_max))
            for key, spectrum in priors
        ]
    except KeyError:
        priors = []
    if not priors:
        priors = [('gt', ts_generation(data, gt_map, 7).reshape(-1))]
    class_values = np.unique(gt_map)
    multi_class = len([v for v in class_values if v > 0]) > 1

    opt_epoch = 0
    max_auc = 0.0
    batch_size = modelConfig['batch_size']
    for e in range(0, modelConfig['epoch'], 1):
        ckpt_file = os.path.join(path, "ckpt_%s_.pt" % e)
        if not os.path.exists(ckpt_file):
            continue
        with torch.no_grad():
            model = SpectralGroupAttention(band=modelConfig['band'], group_length=modelConfig['m'],
                                           channel_dim=modelConfig['channel'], state_size=modelConfig['state_size'],
                                           device=device, layer=modelConfig['layer'])
            model = model.to(device)
            model.load_state_dict(torch.load(ckpt_file, map_location=device))
            model.eval()

            # Shared full-image inference once, reused across priors.
            all_pixel_features = None
            for start_idx, end_idx in _batch_ranges(numpixel, batch_size):
                pixels = torch.unsqueeze(
                    torch.from_numpy(data_matrix[start_idx:end_idx]).to(device),
                    dim=1,
                )
                features = model(pixels).cpu().detach().numpy()
                if all_pixel_features is None:
                    all_pixel_features = np.zeros([numpixel, features.shape[1]],
                                                  dtype=features.dtype)
                all_pixel_features[start_idx:end_idx] = features

            per_class_auc = []
            for key, spectrum in priors:
                target_prior = torch.unsqueeze(
                    torch.from_numpy(spectrum.reshape(-1, 1).T).to(device), dim=1)
                target_features = model(target_prior).cpu().detach().numpy()
                raw_similarity = cosin_similarity(
                    all_pixel_features, target_features).astype(np.float64)
                detection_map, _ = _postprocess_detection(
                    raw_similarity,
                    h,
                    w,
                    modelConfig['delta'],
                    method=modelConfig.get('postprocess', 'rbf'),
                    gamma=modelConfig.get('postprocess_gamma', 10.0),
                )

                if key == 'gt':
                    pos_label = None
                else:
                    pos_label = int(key[1:])
                if pos_label is not None and multi_class:
                    gt_binary = (gt_map == pos_label).astype(np.int32)
                else:
                    gt_binary = (gt_map > 0).astype(np.int32)
                if gt_binary.max() == 0:
                    continue
                per_class_auc.append(
                    _auc_from_maps(gt_binary, detection_map, modelConfig['epision']))

            auc = float(np.mean(per_class_auc)) if per_class_auc else 0.5
            print("epoch %d mean-AUC %.*f (%d classes)"
                  % (e, modelConfig['epision'], auc, len(per_class_auc)))
            if auc > max_auc:
                max_auc = auc
                opt_epoch = e
    print("best mean-AUC %.*f at epoch %d"
          % (modelConfig['epision'], max_auc, opt_epoch))
    return opt_epoch, max_auc

def _resolve_target_spectrum(modelConfig, data, mat):
    '''
    Decide which target spectrum to use for detection.

    Priority:
    1. If a prior index is given (modelConfig['prior']), use the dataset's dN
       first, then optionally fill from wzcl.mat when present.
    2. Otherwise fall back to deriving it from the ground-truth map, preserving
       the original behaviour for the released benchmark datasets.
    Returns a spectrum of shape (band, 1), or None when a requested prior is not
    available and should be skipped.
    '''
    prior = modelConfig.get('prior', None)
    if prior is not None:
        spectrum = resolve_prior_spectrum(
            mat,
            prior,
            library_path=modelConfig.get('prior_library_path', None),
            use_library_fallback=modelConfig.get(
                'use_prior_library_fallback', False
            ),
        )
        if spectrum is None:
            return None
        # Align the prior to the same scale as the normalised cube.
        data_min = modelConfig.get('data_min')
        data_max = modelConfig.get('data_max')
        if data_min is not None and data_max is not None:
            spectrum = normalize_prior_to_data(spectrum, data_min, data_max)
        expected_band = int(modelConfig['band'])
        if spectrum.shape[0] != expected_band:
            print(
                "[prior] skip '%s': length %d does not match dataset band count %d."
                % (prior, spectrum.shape[0], expected_band)
            )
            return None
        return spectrum.reshape(-1, 1)

    if 'map' not in mat:
        raise ValueError(
            "No prior spectrum was provided (--prior) and the dataset has no "
            "'map' to derive one from. Supply a prior index (d1..d255)."
        )
    return ts_generation(data, mat['map'], 7)


def eval(modelConfig: Dict):
    start = time.perf_counter()
    seed_torch(modelConfig['seed'])
    device = torch.device(modelConfig["device"])
    path = modelConfig["save_dir"] + '/' + modelConfig['dataset'] + '/'
    with torch.no_grad():
        data, mat = load_cube(modelConfig["path"])
        has_map = 'map' in mat
        gt_map = mat['map'] if has_map else None
        data, data_min, data_max = standard_with_range(data)
        # Expose the raw range so priors can be aligned to it.
        modelConfig['data_min'] = data_min
        modelConfig['data_max'] = data_max
        target_spectrum = _resolve_target_spectrum(modelConfig, data, mat)
        if target_spectrum is None:
            print('no usable prior spectrum; skip detection.')
            return None
        h, w, c = data.shape
        numpixel = h * w
        data_matrix = np.reshape(data, [-1, c], order='F')
        eval_max_pixels = modelConfig.get("eval_max_pixels")
        out_h, out_w = h, w
        if eval_max_pixels is not None and eval_max_pixels < numpixel:
            numpixel = int(eval_max_pixels)
            data_matrix = data_matrix[:numpixel]
            out_h, out_w = numpixel, 1
            if has_map:
                gt_map = np.reshape(gt_map, [-1], order='F')[:numpixel]
                gt_map = np.reshape(gt_map, [out_h, out_w], order='F')
            print("debug eval: limited to first %d pixels" % numpixel)
        model = SpectralGroupAttention(band=modelConfig['band'], group_length=modelConfig['m'],
                                       channel_dim=modelConfig['channel'], state_size=modelConfig['state_size'],
                                       device=device, layer=modelConfig['layer'])
        model = model.to(device)
        ckpt_path = modelConfig.get(
            "test_load_weight_path",
            os.path.join(path, modelConfig["test_load_weight"]),
        )
        ckpt = torch.load(ckpt_path, map_location=device)
        model.load_state_dict(ckpt)
        print("model load weight done.")
        model.eval()

        batch_size = modelConfig['batch_size']
        detection_map = np.zeros([numpixel])
        target_prior = torch.from_numpy(target_spectrum.T)
        target_prior = target_prior.to(device)
        target_prior = torch.unsqueeze(target_prior, dim=1)
        target_features = model(target_prior)
        target_features = target_features.cpu().detach().numpy()

        for start_idx, end_idx in tqdm(
            _batch_ranges(numpixel, batch_size),
            total=_num_batches(numpixel, batch_size),
            desc="eval pixels",
            dynamic_ncols=True,
        ):
            pixels = data_matrix[start_idx:end_idx]
            pixels = torch.from_numpy(pixels)
            pixels = pixels.to(device)
            pixels = torch.unsqueeze(pixels, dim=1)
            features = model(pixels)
            features = features.cpu().detach().numpy()
            detection_map[start_idx:end_idx] = cosin_similarity(
                features, target_features
            )

        detection_map, _ = _postprocess_detection(
            detection_map,
            out_h,
            out_w,
            modelConfig['delta'],
            method=modelConfig.get('postprocess', 'rbf'),
            gamma=modelConfig.get('postprocess_gamma', 10.0),
        )
        end = time.perf_counter()
        print('excuting time is %s' % (end - start))

        # Persist the detection map so the outer (Java) system can read it back.
        save_result_path = modelConfig.get('result_path', None)
        if save_result_path is None:
            result_dir = os.path.join(
                modelConfig.get("result_save_dir", "./results/"),
                modelConfig['dataset'],
            )
            checkFile(result_dir)
            suffix = ('_%s' % modelConfig['prior']) if modelConfig.get('prior') else ''
            save_result_path = os.path.join(
                result_dir, 'detection%s.mat' % suffix
            )
        else:
            save_result_dir = os.path.dirname(save_result_path)
            if (
                (not save_result_path.lower().endswith('.mat'))
                or os.path.isdir(save_result_path)
            ):
                result_dir = save_result_path
                checkFile(result_dir)
                suffix = ('_%s' % modelConfig['prior']) if modelConfig.get('prior') else ''
                save_result_path = os.path.join(
                    result_dir, 'detection%s.mat' % suffix
                )
            elif save_result_dir:
                checkFile(save_result_dir)
        sio.savemat(save_result_path, {
            'detection_map': detection_map,
        })
        print('detection map saved to %s' % save_result_path)
        _save_detection_png(
            detection_map,
            os.path.splitext(save_result_path)[0] + '.png',
        )

        # AUC is only computable when a ground-truth map is available.
        if not has_map:
            print('no ground-truth map: skip AUC, detection map only.')
            return detection_map

        # If a prior d{k} was used and the map is multi-class, the
        # positive class is the pixels labelled k. Otherwise treat any non-zero
        # pixel as positive.
        pos_label = None
        prior = modelConfig.get('prior')
        if prior is not None:
            try:
                pos_label = int(str(prior).lower().lstrip('d'))
            except ValueError:
                pos_label = None
        _compute_auc(gt_map, detection_map, modelConfig['epision'],
                     pos_label=pos_label)
        return detection_map


def _score_to_detection_map(raw_similarity, h, w, delta):
    '''
    Turn a flat per-pixel cosine-similarity vector into the initial detection
    map: gaussian mapping -> reshape (Fortran order) -> standardise -> clip.
    '''
    detection_map = np.exp(-1 * (raw_similarity - 1) ** 2 / delta)
    detection_map = np.reshape(detection_map, [h, w], order='F')
    detection_map = standard(detection_map)
    detection_map = np.clip(detection_map, 0, 1)
    return detection_map


def _rbf_background_suppression(detection_map, gamma=10.0):
    '''
    RBF post-processing used by the TP-SVTA Pyramid SSM+DCT branch. It keeps
    high-confidence responses and suppresses the low-response background.
    '''
    score = standard(detection_map)
    refined = np.exp(-float(gamma) * (1.0 - score) ** 2)
    refined = standard(refined)
    return np.clip(refined, 0, 1)


def _postprocess_detection(raw_similarity, h, w, delta, method="rbf", gamma=10.0):
    initial_map = _score_to_detection_map(raw_similarity, h, w, delta)
    if str(method).lower() in ("none", "raw", "off"):
        return initial_map, initial_map
    if str(method).lower() == "rbf":
        return _rbf_background_suppression(initial_map, gamma=gamma), initial_map
    raise ValueError("Unknown postprocess method: %s" % method)


def _compute_auc(gt_map, detection_map, epision, pos_label=None):
    '''
    Compute the 5 AUC variants used by this project and print them.

    pos_label: which label in gt_map counts as the positive (target) class. For
    a multi-class map where value k marks the pixels of prior dk, pass k so the
    ground truth is binarised as (gt_map == k). If None, any non-zero pixel is
    treated as positive (single-target maps).
    '''
    gt = np.asarray(gt_map)
    if pos_label is not None:
        binary_gt = (gt == pos_label).astype(np.int32)
    else:
        binary_gt = (gt > 0).astype(np.int32)
    if binary_gt.max() == 0:
        print('  [AUC skipped] no positive pixels for this class in the map.')
        return None
    y_l = np.reshape(binary_gt, [-1, 1], order='F')
    y_p = np.reshape(detection_map, [-1, 1], order='F')
    fpr, tpr, threshold = metrics.roc_curve(y_l, y_p, drop_intermediate=False)
    fpr = fpr[1:]
    tpr = tpr[1:]
    threshold = threshold[1:]
    auc1 = round(metrics.auc(fpr, tpr), epision)
    auc2 = round(metrics.auc(threshold, fpr), epision)
    auc3 = round(metrics.auc(threshold, tpr), epision)
    auc4 = round(auc1 + auc3 - auc2, epision)
    auc5 = round(auc3 / auc2, epision)
    for a in (auc1, auc2, auc3, auc4, auc5):
        print('{:.{precision}f}'.format(a, precision=epision))
    return auc1


def eval_multi(modelConfig: Dict):
    '''
    Multi-prior detection that shares the single heavy full-image inference
    across all available priors.

    The per-pixel feature extraction (the expensive part, ~95% of the cost) is
    run ONCE with the original batch_size, cached, and then reused for every
    prior. Each prior only contributes its own target feature vector and a cheap
    per-pixel cosine similarity, so N priors cost ~= 1 prior instead of N.

    Numerically this is bit-for-bit identical to running eval() once per prior,
    because batch_size is unchanged and the full-image features do not depend on
    which prior is used.

    Writes one detection .mat per prior (detection_d{k}.mat) and returns a dict
    {prior_key: detection_map}.
    '''
    start = time.perf_counter()
    seed_torch(modelConfig['seed'])
    device = torch.device(modelConfig["device"])
    path = modelConfig["save_dir"] + '/' + modelConfig['dataset'] + '/'
    with torch.no_grad():
        data, mat = load_cube(modelConfig["path"])
        has_map = 'map' in mat
        gt_map = mat['map'] if has_map else None
        data, data_min, data_max = standard_with_range(data)
        h, w, c = data.shape
        numpixel = h * w
        data_matrix = np.reshape(data, [-1, c], order='F')
        eval_max_pixels = modelConfig.get("eval_max_pixels")
        out_h, out_w = h, w
        if eval_max_pixels is not None and eval_max_pixels < numpixel:
            numpixel = int(eval_max_pixels)
            data_matrix = data_matrix[:numpixel]
            out_h, out_w = numpixel, 1
            if has_map:
                gt_map = np.reshape(gt_map, [-1], order='F')[:numpixel]
                gt_map = np.reshape(gt_map, [out_h, out_w], order='F')
            print("debug eval_multi: limited to first %d pixels" % numpixel)

        # Dataset priors have priority; missing dN keys can be filled from the
        # optional wzcl library when present. Band-mismatched priors are skipped.
        priors = resolve_all_priors(
            mat,
            library_path=modelConfig.get("prior_library_path", None),
            expected_band=c,
            use_library_fallback=modelConfig.get(
                "use_prior_library_fallback", True
            ),
        )
        if not priors:
            print('no usable priors; skip detection.')
            return {}
        priors = [
            (key, normalize_prior_to_data(spectrum, data_min, data_max))
            for key, spectrum in priors
        ]
        print('resolved %d priors: %s'
              % (len(priors), ', '.join(k for k, _ in priors)))

        model = SpectralGroupAttention(band=modelConfig['band'], group_length=modelConfig['m'],
                                       channel_dim=modelConfig['channel'], state_size=modelConfig['state_size'],
                                       device=device, layer=modelConfig['layer'])
        model = model.to(device)
        ckpt_path = modelConfig.get(
            "test_load_weight_path",
            os.path.join(path, modelConfig["test_load_weight"]),
        )
        ckpt = torch.load(ckpt_path, map_location=device)
        model.load_state_dict(ckpt)
        print("model load weight done.")
        model.eval()

        batch_size = modelConfig['batch_size']

        # --- Heavy part: full-image per-pixel features, computed ONCE. ---
        # Same batching (batch_size unchanged) as eval() so features are
        # bit-for-bit identical to the single-class path.
        feat_dim = None
        all_pixel_features = None
        for start_idx, end_idx in tqdm(
            _batch_ranges(numpixel, batch_size),
            total=_num_batches(numpixel, batch_size),
            desc="shared eval pixels",
            dynamic_ncols=True,
        ):
            pixels = data_matrix[start_idx:end_idx]
            pixels = torch.from_numpy(pixels)
            pixels = pixels.to(device)
            pixels = torch.unsqueeze(pixels, dim=1)
            features = model(pixels).cpu().detach().numpy()
            if all_pixel_features is None:
                feat_dim = features.shape[1]
                all_pixel_features = np.zeros([numpixel, feat_dim], dtype=features.dtype)
            all_pixel_features[start_idx:end_idx] = features
        infer_end = time.perf_counter()
        print('shared full-image inference done in %s s' % (infer_end - start))

        # --- Light part: per-prior target feature + cosine similarity. ---
        result_dir = modelConfig.get('result_dir', None)
        if result_dir is None:
            result_dir = os.path.join(
                modelConfig.get("result_save_dir", "./results/"),
                modelConfig['dataset'],
            )
        checkFile(result_dir)

        detection_maps = {}
        for key, spectrum in priors:
            target_spectrum = spectrum.reshape(-1, 1)
            target_prior = torch.from_numpy(target_spectrum.T).to(device)
            target_prior = torch.unsqueeze(target_prior, dim=1)
            target_features = model(target_prior).cpu().detach().numpy()

            # Match the single-class eval() numerically: there detection_map is a
            # float64 array, so the float32 cosine similarity is up-cast to
            # float64 before the exp/standard/clip post-processing. Do the same
            # here so eval_multi is bit-for-bit identical to per-prior eval().
            raw_similarity = cosin_similarity(
                all_pixel_features, target_features
            ).astype(np.float64)
            detection_map, _ = _postprocess_detection(
                raw_similarity,
                out_h,
                out_w,
                modelConfig['delta'],
                method=modelConfig.get('postprocess', 'rbf'),
                gamma=modelConfig.get('postprocess_gamma', 10.0),
            )
            detection_maps[key] = detection_map

            save_path = os.path.join(result_dir, 'detection_%s.mat' % key)
            sio.savemat(save_path, {
                'detection_map': detection_map,
            })
            print('[%s] detection map saved to %s' % (key, save_path))
            _save_detection_png(
                detection_map,
                os.path.join(result_dir, 'detection_%s.png' % key),
            )

            if has_map:
                # In a multi-class map, prior d{k} corresponds to pixels labelled
                # k. Binarise the ground truth as (map == k) for this class's AUC.
                pos_label = int(key[1:])
                print('[%s] AUC (pos_label=%d):' % (key, pos_label))
                _compute_auc(
                    gt_map, detection_map, modelConfig['epision'],
                    pos_label=pos_label,
                )

        end = time.perf_counter()
        print('eval_multi total time %s s (%d priors)' % (end - start, len(priors)))
        _save_detection_contact_sheet(
            detection_maps,
            os.path.join(result_dir, 'detection_all_vis.png'),
        )
        if not has_map:
            print('no ground-truth map: skip AUC, detection maps only.')
        return detection_maps
