# Seed Schedules

Addresses the reviewer request for "fixed sampling/random-position seed
schedules." This document lists every place a pseudorandom seed is used to
select a sample (as opposed to the deterministic cipher itself, which uses
no randomness beyond the key).

## Correlation and random-position sampling (all tables)

For every table whose values depend on sampled adjacent-pixel correlation
or a randomly chosen pixel position (Tables 3, 4, 6, 7, and 11), the seed
for trial `i` (0-indexed in the row order of that table's `per_trial.csv`)
is:

```text
seed = 20265864 + i
```

`20265864` is the manuscript's paper ID, used as a single fixed base across
every table so the whole set of experiments is generated from one
documented constant. This is passed to `numpy.random.default_rng(seed)`
inside `metrics.py`'s `full_metrics(enc_path, encP_path, seed=...)`, which
in turn seeds both the 10,000-pair adjacent-pixel correlation sampling
(Section 5.3 methodology) and the random-position differential test
(Section 5.4).

## Other seeds

- **Alternative-pool sampling (Table 7 ablation).** The alternative rule
  pool `{22, 28, 108, 134}` was sampled from the 30 symmetry-distinct
  screened candidates (Section 4.3) using Python's `random.seed(42)`
  immediately before the sample draw.

- **Crossed-bootstrap resampling (Sections 5.1, 5.6, 5.9).** All crossed
  (matched) bootstrap resampling in `crossed_design_reanalysis.py` uses
  `numpy.random.default_rng(20265864)`, the same base constant as above,
  fixed once at the top of the script and reused for every bootstrap call,
  so every reported crossed-bootstrap CI is reproducible from this seed
  plus the per-trial CSVs in `Results/`.

## Deterministic (no seed needed)

The mask-collision baseline (`MaskCollisionBaseline.java`, Section 6.5) is
an exhaustive calculation over all 256 control-byte values at each sampled
pixel position and involves no randomness at all.
