# Liu et al. (2025) Reimplementation

Independent reimplementation of:

> Y. Liu, C. Luo, W. Wan, W. Jin, and Z. Qin,  
> “A Secure Medical Image Encryption Scheme Based on Cross-ring Josephus Scrambling and Two-dimensional Cellular Automata,”  
> *IEEE Transactions on Circuits and Systems for Video Technology*, vol. 35, no. 12, pp. 12125–12137, 2025.  
> DOI: 10.1109/TCSVT.2025.3578142

This reimplementation supports the controlled comparison reported in Table 9 of the Hash–ECA manuscript.

## Method

The implementation follows the published pipeline:

1. plaintext-dependent key update;
2. 2D-CICM sequence generation;
3. cross-ring Josephus scrambling;
4. 2-D cellular-automaton diffusion;
5. inverse diffusion and inverse scrambling.

RGB channels are processed independently, following the published method. Details not uniquely specified in the source paper are documented in `Liu2025_ASSUMPTIONS.md`.

## Keys

`liu_keys.csv` contains the 10 fixed parameter sets used for the statistical comparison:

```text
keyIdx,x0_1,y0_1,x0_2,y0_2,a,b
```

The chaotic initial values vary across the 10 rows, while `a = 7` and `b = 5` remain fixed. Statistical metrics use all 10 parameter sets.

Runtime is evaluated separately using the fixed representative parameter set implemented by the single-key benchmark.

## Benchmark protocol

- 10 RGB BMP images, 512×512: Airplane, Baboon, Barbara, Boats, House, Lena, Monarch, Pepper, Sailboat, and Tiffany.
- Statistical evaluation: 10 images × 10 parameter sets = 100 trials.
- Entropy: computed separately for R, G, and B and averaged.
- Correlation: 10,000 adjacent-pixel pairs per horizontal, vertical, and diagonal direction per channel, using sampling seed `20250915`.
- Per-trial correlation summary: `r_avg = (|r_H| + |r_V| + |r_D|) / 3`.
- Differential test: pixel `(255,255)` in zero-based coordinates; R, G, and B are each incremented by `+1 mod 256`.
- NPCR/UACI: computed over the complete 512×512×3 RGB ciphertext byte stream.
- Runtime: 5 warm-up runs and 20 measured runs per image; file I/O and metric computation are excluded.
- Decryption is verified by exact round-trip recovery.

## Results

Values are reported at the same precision as Table 9 of the manuscript.

| Metric | Result |
|---|---:|
| Entropy | 7.999298 |
| `r_avg` | 0.0045 |
| NPCR | 99.6087% |
| UACI | 33.4600% |
| Encryption time | 0.2597 s |
| Decryption time | 0.2546 s |

## Repository files

```text
liu_keys.csv
Liu2025_ASSUMPTIONS.md
java/src/
results/liu_100trial_metrics.csv
results/timing_ms.csv
results/timing_runs_ms.csv
../metrics_common.py
../run_10key_metrics.py
```

`java/src/Main10Key.java` generates the 10-image × 10-parameter-set differential ciphertext pairs. `run_10key_metrics.py` computes the corresponding statistical metrics using the shared definitions in `metrics_common.py`.

Generated ciphertext and differential-test BMP files are not stored in the repository because they can be reproduced from the implementation.
