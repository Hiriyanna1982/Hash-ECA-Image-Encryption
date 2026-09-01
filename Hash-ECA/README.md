# Hash-ECA Image Encryption — Reproducibility Files

This repository contains the reference implementation and supporting files for reproducing the main experiments reported for the Hash-ECA image-encryption scheme.

## Final Parameters

- `B = 8`
- `Rs = 1`
- `Rd = 4`
- `Rpool = {30, 45, 106, 184}`
- Secret key: 128-bit master key

## Repository Contents

```text
Github/
├── keys_used.csv
├── test_vector/
│   └── test_vector_output.txt
├── reference_implementation/
│   ├── CipherUtil.java
│   ├── ScrambleV2.java
│   ├── DiffusionV2.java
│   ├── HashECAEncrypt.java
│   ├── TestVectorGenerator.java
│   ├── ECAExhaustive.java
│   ├── PerfBenchmark.java
│   └── Section6Attacks.java
├── Analysis/
│   └── table1_rule_pool_generation.py
└── Results/
    ├── pool_definitions.json
    ├── pool_evaluation.json
    ├── affine_test.csv
    ├── equivalent_mask_test.csv
    ├── permutation_recovery_test.csv
    └── per_run.csv
```

## Requirements

- OpenJDK 21
- Python 3.12
- NumPy
- pandas

## Compile

From the repository root:

```bash
mkdir -p out
javac -d out reference_implementation/*.java
```

## Run the Cipher

```bash
java -cp out white.HashECAEncrypt <inputImage.bmp> [32HexKey]
```

## Reproduce the Test Vector

```bash
java -cp out white.TestVectorGenerator test_vector_regenerated.txt
```

Compare the generated file with:

```text
test_vector/test_vector_output.txt
```

## Reproduce Table 1 Rule-Pool Selection

```bash
java -cp out white.ECAExhaustive Analysis/eca_outputs.csv
cd Analysis
python3 table1_rule_pool_generation.py
```

The final selected pool is `{30,45,106,184}`. Reference outputs are provided in `Results/pool_definitions.json` and `Results/pool_evaluation.json`.

## Runtime Benchmark

```bash
java -cp out white.PerfBenchmark <imagesDir> <32HexKey> runtime_regenerated.csv
```

The reference per-run timing results are provided in `Results/per_run.csv`.

## Structural Attack Experiments

```bash
mkdir -p attack_results
java -cp out white.Section6Attacks <imagesDir> keys_used.csv attack_results
```

This program reproduces the affine, equivalent-mask, and permutation-recovery experiments. Corresponding reference CSV files are available in the `Results/` folder.

## Reproducibility Note

Deterministic outputs should match when the same inputs, key, parameters, and implementation are used. Runtime measurements may vary across hardware and execution environments.

The benchmark images are not redistributed in this repository. The deterministic test vector can be reproduced without external images.
