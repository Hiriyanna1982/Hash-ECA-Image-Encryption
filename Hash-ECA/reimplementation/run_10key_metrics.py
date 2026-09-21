"""
Compute entropy, r_avg, NPCR, and UACI for a 10-key comparison run.

Usage:
    python3 run_10key_metrics.py <results10_dir> <n_keys> <label>

The results directory must contain files named:
    {image}_k{keyIdx}_C1.bmp
    {image}_k{keyIdx}_C2.bmp

The metric definitions and fixed correlation sampling schedule are imported
from metrics_common.py.
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

from metrics_common import (
    sample_coords,
    entropy_avg_rgb,
    correlation_r_avg,
    npcr_uaci_full_rgb,
)

NAMES = [
    "Airplane", "Baboon", "Barbara", "Boats", "House",
    "Lena", "Monarch", "Pepper", "Sailboat", "Tiffany",
]


def load_rgb(path):
    return np.array(Image.open(path).convert("RGB"), dtype=np.uint8)


def main():
    if len(sys.argv) != 4:
        raise SystemExit(
            "Usage: python3 run_10key_metrics.py "
            "<results10_dir> <n_keys> <label>"
        )

    results_dir = Path(sys.argv[1])
    n_keys = int(sys.argv[2])
    label = sys.argv[3]

    coords = None
    entropies, ravgs, npcrs, uacis = [], [], [], []
    trial_rows = []

    for name in NAMES:
        for k in range(1, n_keys + 1):
            c1_path = results_dir / f"{name}_k{k}_C1.bmp"
            c2_path = results_dir / f"{name}_k{k}_C2.bmp"

            if not c1_path.exists() or not c2_path.exists():
                raise FileNotFoundError(
                    f"Missing trial pair: {c1_path} / {c2_path}"
                )

            c1 = load_rgb(c1_path)
            c2 = load_rgb(c2_path)
            m, n, _ = c1.shape

            if coords is None:
                coords = sample_coords(m, n)

            _, _, _, h_avg = entropy_avg_rgb(c1)
            _, _, _, r_avg = correlation_r_avg(c1, coords)
            npcr, uaci = npcr_uaci_full_rgb(c1, c2)

            entropies.append(h_avg)
            ravgs.append(r_avg)
            npcrs.append(npcr)
            uacis.append(uaci)
            trial_rows.append((name, k, h_avg, r_avg, npcr, uaci))

    print(f"=== {label}: n={len(entropies)} trials ===")
    print(f"entropy_avg = {np.mean(entropies):.6f}")
    print(f"r_avg       = {np.mean(ravgs):.6f} -> 4dp = {np.mean(ravgs):.4f}")
    print(f"NPCR (%)    = {np.mean(npcrs):.4f}")
    print(f"UACI (%)    = {np.mean(uacis):.4f}")

    out_csv = results_dir / f"{label}_100trial_metrics.csv"
    with out_csv.open("w", encoding="utf-8", newline="") as f:
        f.write("image,keyIdx,entropy_avg,r_avg,NPCR_pct,UACI_pct\n")
        for row in trial_rows:
            f.write(",".join(str(x) for x in row) + "\n")

    print(f"Wrote {out_csv}")


if __name__ == "__main__":
    main()
