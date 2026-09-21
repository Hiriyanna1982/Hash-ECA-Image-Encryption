# Dong et al. (2022) Reimplementation

Independent reimplementation of:

> Y. Dong, G. Zhao, Y. Ma, Z. Pan, and R. Wu,  
> “A novel image encryption scheme based on pseudo-random coupled map lattices with hybrid elementary cellular automata,”  
> *Information Sciences*, vol. 593, pp. 121–154, 2022.  
> DOI: 10.1016/j.ins.2022.01.031

This reimplementation supports the controlled comparison reported in Table 9 of the Hash–ECA manuscript.

## Method

The implementation follows the published color-image pipeline:

1. plaintext-dependent key-seed generation;
2. Chirikov Standard Map initialization;
3. PRCML-HECA evolution and series-wound key-seed processing;
4. permutation generation;
5. substitution/diffusion using the generated keystream;
6. inverse processing for decryption.

The hybrid ECA uses the 34-rule lookup table reported in the paper. Under-specified details and source-paper inconsistencies are documented in `Dong2022_ASSUMPTIONS.md`.

## Keys

`dong_keys.csv` contains the 10 fixed 256-bit hexadecimal keys used for the statistical comparison. Statistical metrics use all 10 keys.

Runtime is evaluated separately using the fixed representative key implemented by the single-key benchmark. The plaintext-dependent key-seed procedure uses the fixed benchmark timestamp:

```text
20260101000000
```

## Benchmark protocol

- 10 RGB BMP images, 512×512: Airplane, Baboon, Barbara, Boats, House, Lena, Monarch, Pepper, Sailboat, and Tiffany.
- Statistical evaluation: 10 images × 10 keys = 100 trials.
- Entropy: computed separately for R, G, and B and averaged.
- Correlation: 10,000 adjacent-pixel pairs per horizontal, vertical, and diagonal direction per channel, using sampling seed `20250915`.
- Per-trial correlation summary: `r_avg = (|r_H| + |r_V| + |r_D|) / 3`.
- Differential test: pixel `(255,255)` in zero-based coordinates; R, G, and B are each incremented by `+1 mod 256`.
- NPCR/UACI: computed over the complete 512×512×3 RGB ciphertext byte stream.
- Runtime: 5 warm-up runs and 20 measured runs per image; file I/O and metric computation are excluded.
- Encryption timing includes plaintext-dependent key-seed generation.
- Decryption is verified by exact round-trip recovery.

## Results

Values are reported at the same precision as Table 9 of the manuscript.

| Metric | Result |
|---|---:|
| Entropy | 7.999296 |
| `r_avg` | 0.0048 |
| NPCR | 99.6095% |
| UACI | 33.4619% |
| Encryption time | 1.9435 s |
| Decryption time | 0.5187 s |

## Repository files

```text
dong_keys.csv
Dong2022_ASSUMPTIONS.md
java/src/
results/dong_100trial_metrics.csv
results/timing_ms.csv
results/timing_runs_ms.csv
../metrics_common.py
../run_10key_metrics.py
```

`java/src/Main10Key.java` generates the 10-image × 10-key differential ciphertext pairs. `run_10key_metrics.py` computes the corresponding statistical metrics using the shared definitions in `metrics_common.py`.

Generated ciphertext and differential-test BMP files are not stored in the repository because they can be reproduced from the implementation.
