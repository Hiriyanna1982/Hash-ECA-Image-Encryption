"""
Shared metric definitions for the controlled 10-image × 10-key comparison.

Conventions:
- Entropy: Shannon entropy is computed independently for R, G, and B,
  then averaged across channels.
- Correlation: 10,000 adjacent-pixel pairs are sampled for each of the
  horizontal, vertical, and diagonal directions using a fixed reproducible
  sampling seed. Pearson correlation is computed per channel and direction;
  channel values are averaged to obtain r_H, r_V, and r_D, followed by
  r_avg = (|r_H| + |r_V| + |r_D|) / 3.
- NPCR/UACI: computed over the complete RGB byte stream.
"""

import numpy as np

SEED = 20250915
N_PAIRS = 10000


def sample_coords(m, n, seed=SEED, n_pairs=N_PAIRS):
    """Return reproducible adjacent-pixel coordinate pairs for H/V/D."""
    rng = np.random.default_rng(seed)

    hi = rng.integers(0, m, size=n_pairs)
    hj = rng.integers(0, n - 1, size=n_pairs)

    vi = rng.integers(0, m - 1, size=n_pairs)
    vj = rng.integers(0, n, size=n_pairs)

    di = rng.integers(0, m - 1, size=n_pairs)
    dj = rng.integers(0, n - 1, size=n_pairs)

    return {
        "H": (hi, hj, hi, hj + 1),
        "V": (vi, vj, vi + 1, vj),
        "D": (di, dj, di + 1, dj + 1),
    }


def shannon_entropy(channel_2d):
    """Shannon entropy (base 2) of an 8-bit single-channel image."""
    hist = np.bincount(
        channel_2d.ravel().astype(np.uint8), minlength=256
    ).astype(np.float64)
    p = hist[hist > 0] / hist.sum()
    return float(-np.sum(p * np.log2(p)))


def entropy_avg_rgb(rgb_img):
    """Return (H_R, H_G, H_B, H_avg) for an H×W×3 uint8 image."""
    hr = shannon_entropy(rgb_img[:, :, 0])
    hg = shannon_entropy(rgb_img[:, :, 1])
    hb = shannon_entropy(rgb_img[:, :, 2])
    return hr, hg, hb, (hr + hg + hb) / 3.0


def pearson(a, b):
    a = a.astype(np.float64)
    b = b.astype(np.float64)
    if a.std() == 0 or b.std() == 0:
        return 0.0
    return float(np.corrcoef(a, b)[0, 1])


def correlation_r_avg(rgb_img, coords):
    """Return (r_H, r_V, r_D, r_avg)."""
    r_dirs = {}
    for d in ("H", "V", "D"):
        i1, j1, i2, j2 = coords[d]
        rs = []
        for c in range(3):
            plane = rgb_img[:, :, c]
            rs.append(pearson(plane[i1, j1], plane[i2, j2]))
        r_dirs[d] = sum(rs) / 3.0

    r_avg = (
        abs(r_dirs["H"]) + abs(r_dirs["V"]) + abs(r_dirs["D"])
    ) / 3.0
    return r_dirs["H"], r_dirs["V"], r_dirs["D"], r_avg


def npcr_uaci_full_rgb(c1_img, c2_img):
    """Return NPCR and UACI percentages over the complete RGB byte stream."""
    a = c1_img.astype(np.int32).ravel()
    b = c2_img.astype(np.int32).ravel()
    n_total = a.size

    npcr = 100.0 * np.count_nonzero(a != b) / n_total
    uaci = 100.0 * np.sum(np.abs(a - b)) / (255.0 * n_total)
    return float(npcr), float(uaci)
