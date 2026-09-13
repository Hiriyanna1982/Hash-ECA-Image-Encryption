import numpy as np
from PIL import Image

def load_rgb(path):
    return np.array(Image.open(path).convert("RGB")).astype(np.int64)

def channel_entropy(chan):
    hist = np.bincount(chan.flatten(), minlength=256).astype(np.float64)
    p = hist / hist.sum()
    p = p[p > 0]
    return float(-np.sum(p * np.log2(p)))

def entropy_rgb(img):
    return float(np.mean([channel_entropy(img[:, :, c]) for c in range(3)]))

def sample_correlation_channel(chan, direction, n_pairs=10000, seed=None):
    H, W = chan.shape
    rng = np.random.default_rng(seed)
    if direction == 'H':
        xs = rng.integers(0, W-1, size=n_pairs); ys = rng.integers(0, H, size=n_pairs)
        x2s, y2s = xs+1, ys
    elif direction == 'V':
        xs = rng.integers(0, W, size=n_pairs); ys = rng.integers(0, H-1, size=n_pairs)
        x2s, y2s = xs, ys+1
    elif direction == 'D':
        xs = rng.integers(0, W-1, size=n_pairs); ys = rng.integers(0, H-1, size=n_pairs)
        x2s, y2s = xs+1, ys+1
    a = chan[ys, xs]; b = chan[y2s, x2s]
    if a.std() == 0 or b.std() == 0:
        return 0.0
    return float(np.corrcoef(a, b)[0, 1])

def full_correlation_report(img, n_pairs=10000, seed=42):
    per_dir = {}
    for direction in ['H', 'V', 'D']:
        rgb = [sample_correlation_channel(img[:, :, c], direction, n_pairs, seed=seed) for c in range(3)]
        per_dir[direction] = float(np.mean(rgb))
    rH, rV, rD = per_dir['H'], per_dir['V'], per_dir['D']
    r_avg = (abs(rH) + abs(rV) + abs(rD)) / 3.0
    return {"rH": rH, "rV": rV, "rD": rD, "r_avg": r_avg}

def npcr_uaci(img1, img2):
    assert img1.shape == img2.shape
    H, W, C = img1.shape
    npcrs, uacis = [], []
    for c in range(C):
        a = img1[:, :, c]; b = img2[:, :, c]
        diff = (a != b)
        npcr = 100.0 * diff.sum() / (H * W)
        uaci = 100.0 * np.sum(np.abs(a.astype(np.float64) - b.astype(np.float64))) / (255.0 * H * W)
        npcrs.append(npcr); uacis.append(uaci)
    return float(np.mean(npcrs)), float(np.mean(uacis))

def full_metrics(enc_path, encP_path, seed):
    img = load_rgb(enc_path)
    imgP = load_rgb(encP_path)
    ent = entropy_rgb(img)
    corr = full_correlation_report(img, n_pairs=10000, seed=seed)
    npcr, uaci = npcr_uaci(img, imgP)
    return {"entropy": ent, "rH": corr["rH"], "rV": corr["rV"], "rD": corr["rD"],
            "r_avg": corr["r_avg"], "npcr": npcr, "uaci": uaci}
