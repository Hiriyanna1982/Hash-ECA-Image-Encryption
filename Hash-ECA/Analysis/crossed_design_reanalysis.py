"""
Crossed-design (image x key) reanalysis, addressing Reviewer 1 Comment 4:
"The reported t confidence intervals pool the crossed 10-image by 10-key
experiment as 100 independent trials. This can obscure image and key
effects. Please report variability by image and by key and use a
crossed-design analysis or resampling procedure, particularly when
interpreting the small differences in the ablation and round-sensitivity
results."

For each metric, this script:
  1. Decomposes variance into image effect, key effect, and residual
     (a balanced two-way ANOVA-style decomposition), reporting the
     percentage of total variance attributable to each source.
  2. Constructs a crossed (two-way cluster) bootstrap confidence interval
     for the grand mean, resampling images and keys independently with
     replacement -- this does not assume the trials are i.i.d.
  3. For the ablation (Table 2) and round-sensitivity (Table 11) results,
     applies the same crossed bootstrap to the *difference* between
     configurations/round-counts, since these share the same image x key
     grid and are therefore paired, not independent, comparisons.
"""
import numpy as np
import pandas as pd
import json

RNG_SEED = 20265864  # paper ID, for a fixed reproducible resampling seed
N_BOOT = 10000


def variance_decomposition(df, value_col, image_col="image", key_col="keyIdx"):
    """Balanced two-way ANOVA variance decomposition (no interaction term,
    since each image x key cell has exactly one observation)."""
    images = sorted(df[image_col].unique())
    keys = sorted(df[key_col].unique())
    I, K = len(images), len(keys)

    pivot = df.pivot(index=image_col, columns=key_col, values=value_col).loc[images, keys]
    Y = pivot.values.astype(float)
    grand_mean = Y.mean()
    image_means = Y.mean(axis=1)   # per-image, averaged over keys
    key_means = Y.mean(axis=0)     # per-key, averaged over images

    SS_image = K * np.sum((image_means - grand_mean) ** 2)
    SS_key = I * np.sum((key_means - grand_mean) ** 2)
    SS_total = np.sum((Y - grand_mean) ** 2)
    SS_residual = SS_total - SS_image - SS_key

    df_image, df_key = I - 1, K - 1
    df_residual = (I - 1) * (K - 1)

    MS_image = SS_image / df_image
    MS_key = SS_key / df_key
    MS_residual = SS_residual / df_residual if df_residual > 0 else np.nan

    pct_image = 100 * SS_image / SS_total if SS_total > 0 else 0.0
    pct_key = 100 * SS_key / SS_total if SS_total > 0 else 0.0
    pct_residual = 100 * SS_residual / SS_total if SS_total > 0 else 0.0

    F_image = MS_image / MS_residual if MS_residual and MS_residual > 0 else np.nan
    F_key = MS_key / MS_residual if MS_residual and MS_residual > 0 else np.nan

    return {
        "grand_mean": float(grand_mean),
        "n_images": I, "n_keys": K,
        "image_means": {str(im): float(v) for im, v in zip(images, image_means)},
        "key_means": {str(k): float(v) for k, v in zip(keys, key_means)},
        "SS_image": float(SS_image), "SS_key": float(SS_key), "SS_residual": float(SS_residual),
        "pct_var_image": float(pct_image), "pct_var_key": float(pct_key), "pct_var_residual": float(pct_residual),
        "F_image": float(F_image) if not np.isnan(F_image) else None,
        "F_key": float(F_key) if not np.isnan(F_key) else None,
        "df_image": df_image, "df_key": df_key, "df_residual": df_residual,
    }


def crossed_bootstrap_ci(df, value_col, image_col="image", key_col="keyIdx",
                          n_boot=N_BOOT, seed=RNG_SEED, alpha=0.05):
    """Two-way cluster bootstrap: resample the set of images (with
    replacement) and the set of keys (with replacement) independently,
    rebuild the resampled grid, and recompute the grand mean each time."""
    images = sorted(df[image_col].unique())
    keys = sorted(df[key_col].unique())
    I, K = len(images), len(keys)
    pivot = df.pivot(index=image_col, columns=key_col, values=value_col).loc[images, keys].values.astype(float)

    rng = np.random.default_rng(seed)
    boot_means = np.empty(n_boot)
    for b in range(n_boot):
        img_idx = rng.integers(0, I, size=I)
        key_idx = rng.integers(0, K, size=K)
        resampled = pivot[np.ix_(img_idx, key_idx)]
        boot_means[b] = resampled.mean()

    lo, hi = np.percentile(boot_means, [100 * alpha / 2, 100 * (1 - alpha / 2)])
    return {
        "point_estimate": float(pivot.mean()),
        "bootstrap_mean": float(boot_means.mean()),
        "bootstrap_sd": float(boot_means.std(ddof=1)),
        "ci_95_lower": float(lo), "ci_95_upper": float(hi),
        "n_boot": n_boot,
    }


def crossed_bootstrap_diff_ci(df_a, df_b, value_col, image_col="image", key_col="keyIdx",
                               n_boot=N_BOOT, seed=RNG_SEED, alpha=0.05):
    """Same crossed bootstrap, but for the *difference* between two
    configurations measured on the same image x key grid (paired, not
    independent) -- used for the ablation and round-sensitivity
    comparisons."""
    images = sorted(df_a[image_col].unique())
    keys = sorted(df_a[key_col].unique())
    assert images == sorted(df_b[image_col].unique())
    assert keys == sorted(df_b[key_col].unique())
    I, K = len(images), len(keys)

    piv_a = df_a.pivot(index=image_col, columns=key_col, values=value_col).loc[images, keys].values.astype(float)
    piv_b = df_b.pivot(index=image_col, columns=key_col, values=value_col).loc[images, keys].values.astype(float)

    rng = np.random.default_rng(seed)
    boot_diffs = np.empty(n_boot)
    for b in range(n_boot):
        img_idx = rng.integers(0, I, size=I)
        key_idx = rng.integers(0, K, size=K)
        # SAME resampled image/key indices applied to both grids -> proper paired resampling
        diff_grid = piv_a[np.ix_(img_idx, key_idx)] - piv_b[np.ix_(img_idx, key_idx)]
        boot_diffs[b] = diff_grid.mean()

    lo, hi = np.percentile(boot_diffs, [100 * alpha / 2, 100 * (1 - alpha / 2)])
    point_diff = float(piv_a.mean() - piv_b.mean())
    crosses_zero = lo <= 0 <= hi
    return {
        "point_diff": point_diff,
        "bootstrap_mean_diff": float(boot_diffs.mean()),
        "bootstrap_sd_diff": float(boot_diffs.std(ddof=1)),
        "ci_95_lower": float(lo), "ci_95_upper": float(hi),
        "ci_includes_zero": bool(crosses_zero),
        "n_boot": n_boot,
    }


if __name__ == "__main__":
    results = {}

    # NOTE ON NAMING: variable names below (t3, t4, t7, t2, t11) reflect this
    # script's internal/historical labels, which predate the manuscript's
    # final table renumbering. Final-paper correspondence: t3 -> Table 3
    # (entropy/correlation), t4 -> Table 4 (differential), t7 -> Table 6
    # (key-sensitivity), t2 -> Table 7 (rule-pool ablation), t11 -> Table 11
    # (round-count sensitivity). File paths use descriptive folder names
    # rather than these internal labels to avoid the same confusion.

    # ---- Table 3: entropy, r_avg (10 images x 10 keys) ----
    t3 = pd.read_csv("../Results/primary_dataset/entropy_correlation/per_trial.csv")
    results["table3_entropy"] = {
        "variance_decomposition": variance_decomposition(t3, "entropy"),
        "crossed_bootstrap_ci": crossed_bootstrap_ci(t3, "entropy"),
    }
    results["table3_ravg"] = {
        "variance_decomposition": variance_decomposition(t3, "r_avg"),
        "crossed_bootstrap_ci": crossed_bootstrap_ci(t3, "r_avg"),
    }

    # ---- Table 4: NPCR/UACI, controlled and random settings ----
    t4c = pd.read_csv("../Results/primary_dataset/differential_analysis/controlled_npcr_uaci.csv")
    t4r = pd.read_csv("../Results/primary_dataset/differential_analysis/random_npcr_uaci.csv")
    for name, df in [("controlled", t4c), ("random", t4r)]:
        for metric in ["npcr", "uaci"]:
            results[f"table4_{name}_{metric}"] = {
                "variance_decomposition": variance_decomposition(df, metric),
                "crossed_bootstrap_ci": crossed_bootstrap_ci(df, metric),
            }

    # ---- Table 6 (final numbering): key-sensitivity NPCR/UACI ----
    t7 = pd.read_csv("../Results/primary_dataset/key_sensitivity/per_trial.csv")
    for metric in ["npcr", "uaci"]:
        results[f"table7_{metric}"] = {
            "variance_decomposition": variance_decomposition(t7, metric),
            "crossed_bootstrap_ci": crossed_bootstrap_ci(t7, metric),
        }

    # ---- Table 7 (final numbering): rule-pool ablation (3 images x 10 keys), pairwise config differences ----
    t2 = pd.read_csv("../Results/round2/ablation_pool_comparison/per_trial.csv")
    configs = t2["config"].unique().tolist()
    for metric in ["entropy", "r_avg", "npcr", "uaci"]:
        pairwise = {}
        for i in range(len(configs)):
            for j in range(i + 1, len(configs)):
                a, b = configs[i], configs[j]
                df_a = t2[t2.config == a]
                df_b = t2[t2.config == b]
                pairwise[f"{a}_vs_{b}"] = crossed_bootstrap_diff_ci(df_a, df_b, metric)
        results[f"table2_ablation_{metric}_pairwise"] = pairwise

    # ---- Table 11: round-count sensitivity (3 images x 10 keys), pairwise Rd differences ----
    t11 = pd.read_csv("../Results/round2/round_count_sensitivity/per_trial.csv")
    rds = sorted(t11["rd"].unique().tolist())
    for metric in ["entropy", "r_avg", "npcr", "uaci"]:
        pairwise = {}
        for i in range(len(rds)):
            for j in range(i + 1, len(rds)):
                a, b = rds[i], rds[j]
                df_a = t11[t11.rd == a]
                df_b = t11[t11.rd == b]
                pairwise[f"Rd{a}_vs_Rd{b}"] = crossed_bootstrap_diff_ci(df_a, df_b, metric)
        results[f"table11_roundcount_{metric}_pairwise"] = pairwise

    with open("crossed_design_reanalysis.json", "w") as f:
        json.dump(results, f, indent=2)

    # ---- printed summary ----
    print("=== Variance decomposition summary (% of total variance) ===")
    for key in ["table3_entropy", "table3_ravg", "table4_controlled_npcr", "table4_controlled_uaci",
                "table4_random_npcr", "table4_random_uaci", "table7_npcr", "table7_uaci"]:
        vd = results[key]["variance_decomposition"]
        print(f"{key:<28} image={vd['pct_var_image']:6.2f}%  key={vd['pct_var_key']:6.2f}%  residual={vd['pct_var_residual']:6.2f}%")

    print()
    print("=== Table 2 ablation: pairwise differences (crossed bootstrap 95% CI) ===")
    for metric in ["entropy", "r_avg", "npcr", "uaci"]:
        print(f"-- {metric} --")
        for pair, res in results[f"table2_ablation_{metric}_pairwise"].items():
            sig = "NOT sig. (CI includes 0)" if res["ci_includes_zero"] else "SIGNIFICANT"
            print(f"  {pair}: diff={res['point_diff']:.6f}  95% CI=[{res['ci_95_lower']:.6f}, {res['ci_95_upper']:.6f}]  {sig}")

    print()
    print("=== Table 11 round-sensitivity: pairwise Rd differences (crossed bootstrap 95% CI) ===")
    for metric in ["entropy", "r_avg", "npcr", "uaci"]:
        print(f"-- {metric} --")
        for pair, res in results[f"table11_roundcount_{metric}_pairwise"].items():
            sig = "NOT sig. (CI includes 0)" if res["ci_includes_zero"] else "SIGNIFICANT"
            print(f"  {pair}: diff={res['point_diff']:.6f}  95% CI=[{res['ci_95_lower']:.6f}, {res['ci_95_upper']:.6f}]  {sig}")
