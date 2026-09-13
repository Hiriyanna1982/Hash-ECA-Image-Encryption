# Hash-ECA Image Encryption — Reproducibility Files

This repository contains the reference implementation and the files required to reproduce the experiments reported for the manuscript:

**“Hash-Driven Multi-Rule ECA Image Encryption Using Hierarchical Scrambling and Feedback Diffusion”**  
Paper ID: **20265864**

## Final Parameters

- Block size: `B = 8`
- Scrambling rounds: `Rs = 1`
- Diffusion rounds: `Rd = 4`
- ECA rule pool: `Rpool = {30, 45, 106, 184}`
- Secret key: 128-bit master key

## Repository Contents

```text
Hash-ECA-Image-Encryption/
├── README.md
├── keys_used.csv
├── test_vector/
│   └── test_vector_output.txt
├── reference_implementation/
│   └── [Java source files]
├── Analysis/
│   ├── table1_rule_pool_generation.py
│   ├── metrics.py
│   └── crossed_design_reanalysis.py
├── Documentation/
│   ├── SEED_SCHEDULES.md
│   ├── SCALABILITY_IMAGE_PREPARATION.md
│   └── MEMORY_BENCHMARK.md
└── Results/
    ├── [main-experiment reference results]
    ├── primary_dataset/
    └── round2/
```

## Requirements

The reported experiments used:

- OpenJDK 21
- Python 3.12
- NumPy
- pandas
- Pillow
- SciPy

Install the required Python packages, if needed:

```bash
python3 -m pip install numpy pandas pillow scipy
```

## Compile

From the repository root:

```bash
mkdir -p out
javac -d out reference_implementation/*.java
```

## Evaluation Keys

The exact 10 evaluation keys used in the manuscript are provided in:

```text
keys_used.csv
```

## Run the Cipher

```bash
java -cp out white.HashECAEncrypt <inputImage.bmp> [32HexKey]
```

## Reproduce the Test Vector

```bash
java -cp out white.TestVectorGenerator test_vector_regenerated.txt
```

Compare the generated output with:

```text
test_vector/test_vector_output.txt
```

## Rule-Pool Selection

Generate the exhaustive ECA outputs:

```bash
java -cp out white.ECAExhaustive Analysis/eca_outputs.csv
```

Then run:

```bash
cd Analysis
python3 table1_rule_pool_generation.py
```

Reference rule-pool outputs are provided in the `Results/` folder.

## Main Runtime Benchmark

```bash
java -cp out white.PerfBenchmark <imagesDir> <32HexKey> runtime_regenerated.csv
```

The manuscript used key 1 from `keys_used.csv` for the reported timing benchmark.

Published benchmark environment:

- OpenJDK 21.0.11
- Ubuntu 24.04.4 LTS
- single-core Intel Xeon @ 2.10 GHz
- 3.9 GB RAM

Runtime measurements may vary across hardware and JVM environments.

## Peak-Memory Benchmark

```bash
java -Xmx4g -cp out white.PeakMemoryBenchmark <imagePath> <32HexKey> memory_regenerated.csv
```

The exact memory-measurement procedure and configuration are documented in:

```text
Documentation/MEMORY_BENCHMARK.md
```

## Structural Chosen-Plaintext Tests

```bash
mkdir -p attack_results
java -cp out white.Section6Attacks <imagesDir> keys_used.csv attack_results
```

This reproduces the whole-cipher affine test, additive equivalent-mask test, and permutation-recovery test. Reference outputs are provided in the `Results/` folder.

## Component-Level Ablation

### Full hierarchy vs. pixel-only scrambling

```bash
java -Xmx4g -cp out white.ScramblingHierarchyAblation <imagesDir> keys_used.csv hierarchy_results
```

Reference outputs:

```text
Results/round2/hierarchy_ablation/
```

### ECA vs. no-ECA baseline

```bash
java -Xmx4g -cp out white.ECABaselineAblation <imagesDir> keys_used.csv eca_baseline_results
java -cp out white.MaskLinearityTest
```

Reference outputs:

```text
Results/round2/eca_baseline/
```

### Selected pool vs. Rule 30 vs. alternative pool

Reference per-trial and summary results are provided in:

```text
Results/round2/ablation_pool_comparison/
```

## Diffusion-Round Sensitivity and Single-Bit Avalanche

Reference results for `Rd = 1, 2, 3, 4` are provided in:

```text
Results/round2/round_count_sensitivity/
```

Run the single-bit avalanche experiment with:

```bash
java -Xmx4g -cp out white.SingleBitAvalanche <imagesDir> keys_used.csv avalanche_results
```

Reference outputs:

```text
Results/round2/avalanche/
```

## Adaptive Chosen-Plaintext State-Update Test

```bash
java -Xmx4g -cp out white.AdaptiveControlStateAttack keys_used.csv adaptive_results
```

Run the independent-control-byte mask-collision baseline with:

```bash
java -cp out white.MaskCollisionBaseline mask_collision_baseline_regenerated.csv
```

Reference outputs:

```text
Results/round2/adaptive_attack/
```

## Crossed-Design Statistical Reanalysis

The revised statistical analysis uses a two-way image × key variance decomposition and a matched crossed bootstrap with 10,000 replicates.

Run:

```bash
cd Analysis
python3 crossed_design_reanalysis.py
```

The required per-trial input files are included under:

```text
Results/primary_dataset/
Results/round2/ablation_pool_comparison/
Results/round2/round_count_sensitivity/
```

Reference crossed-design output is provided in:

```text
Results/round2/crossed_bootstrap/
```

## Random-Seed Schedules

The exact fixed seed schedules used for:

- adjacent-pixel correlation sampling,
- random-position differential testing,
- alternative rule-pool sampling, and
- crossed-bootstrap resampling

are documented in:

```text
Documentation/SEED_SCHEDULES.md
```

## Scalability Images and Crop Coordinates

The benchmark images are not redistributed. Exact Wikimedia Commons source identifiers, locally used source dimensions, and 0-indexed center-crop coordinates for the 256×256, 512×512, and 1024×1024 test images are documented in:

```text
Documentation/SCALABILITY_IMAGE_PREPARATION.md
```

The three source images are:

- `Bird (26576011703).jpg`
- `Mountain Lake Sierra Mountains.jpg`
- `Colorful Bell Peppers.JPG`

The documented preparation uses center cropping only, with no resizing or interpolation.

## Reproducibility Notes

- Deterministic cipher outputs should match exactly when the same image, key, parameters, and implementation are used.
- The test vector provides an implementation-level correctness check independent of external benchmark images.
- Statistical analyses use the fixed seed schedules documented in `Documentation/SEED_SCHEDULES.md`.
- Runtime and peak-memory measurements may vary with processor, operating system, JVM version, JIT state, garbage collection, and background load.
