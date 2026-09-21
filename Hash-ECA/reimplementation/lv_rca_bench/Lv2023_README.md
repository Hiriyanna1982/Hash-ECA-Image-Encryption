# Lv et al. (2023) Reimplementation

Independent Java port of:

> W. Lv, J. Chen, X. Chai, and C. Fu,  
> “A robustness-improved image encryption scheme utilizing Life-liked cellular automaton,”  
> *Nonlinear Dynamics*, vol. 111, no. 4, pp. 3887–3907, 2023.  
> DOI: 10.1007/s11071-022-08021-1

Authors' reference implementation:

```text
https://github.com/NEUboy/encode_RCA
```

This reimplementation supports the controlled comparison reported in Table 9 of the Hash–ECA manuscript.

## Method

The implementation follows the published/reference grayscale pipeline:

1. key-dependent 2D-LSCM sequence generation;
2. disturbing-pixel pretreatment;
3. block permutation;
4. reversible Life-like cellular-automaton diffusion;
5. inverse diffusion, inverse permutation, and inverse pretreatment.

The original scheme is grayscale-only. For the matched RGB comparison, the complete grayscale pipeline is applied independently to R, G, and B. The chaotic generator and cipher state are reinitialized from the selected trial key for each channel. This extension is documented in `Lv2023_ASSUMPTIONS.md`.

## Keys

`lv_keys.csv` contains the 10 fixed 256-bit hexadecimal keys used for the statistical comparison. For each statistical trial, the same selected key is applied independently to R, G, and B. Statistical metrics use all 10 keys.

Runtime is evaluated separately using the fixed published/reference key implemented by the single-key benchmark.

## Benchmark protocol

- 10 RGB BMP images, 512×512: Airplane, Baboon, Barbara, Boats, House, Lena, Monarch, Pepper, Sailboat, and Tiffany.
- Statistical evaluation: 10 images × 10 keys = 100 trials.
- Entropy: computed separately for R, G, and B and averaged.
- Correlation: 10,000 adjacent-pixel pairs per horizontal, vertical, and diagonal direction per channel, using sampling seed `20250915`.
- Per-trial correlation summary: `r_avg = (|r_H| + |r_V| + |r_D|) / 3`.
- Differential test: pixel `(255,255)` in zero-based coordinates; R, G, and B are each incremented by `+1 mod 256`.
- NPCR/UACI: computed over the complete 512×512×3 RGB ciphertext byte stream.
- Runtime: 5 warm-up runs and 20 measured runs per image; file I/O and metric computation are excluded.
- Encryption/decryption timing covers the complete R+G+B processing.
- Decryption is verified by exact round-trip recovery.

## Results

Values are reported at the same precision as Table 9 of the manuscript.

| Metric | Result |
|---|---:|
| Entropy | 7.999300 |
| `r_avg` | 0.0047 |
| NPCR | 99.6054% |
| UACI | 33.4597% |
| Encryption time | 3.9092 s |
| Decryption time | 3.9609 s |

## Repository files

```text
lv_keys.csv
Lv2023_ASSUMPTIONS.md
java/src/
results/lv_100trial_metrics.csv
results/timing_ms.csv
results/timing_runs_ms.csv
../metrics_common.py
../run_10key_metrics.py
```

`java/src/Main10Key.java` generates the 10-image × 10-key differential ciphertext pairs. `run_10key_metrics.py` computes the corresponding statistical metrics using the shared definitions in `metrics_common.py`.

Generated ciphertext and differential-test BMP files are not stored in the repository because they can be reproduced from the implementation.
