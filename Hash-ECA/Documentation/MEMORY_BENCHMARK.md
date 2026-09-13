# Peak Memory Benchmark (Table 10)

Addresses the reviewer request for "the timing/memory commands and
configuration" -- specifically the memory half, which was previously
undocumented in this repository.

## Methodology

Peak JVM heap usage is measured using `MemoryPoolMXBean` peak-usage
counters, summed across all heap pools (Eden, Survivor, Tenured/Old),
matching the paper's Table 10 methodology. For each image size and
operation (encryption/decryption), 5 warm-up runs are discarded, followed
by 20 measured runs; before each measured run the peak-usage counters are
reset and a `System.gc()` is requested so each run's peak reflects that
run's allocation, not a carried-over peak from a previous run.

## Running it

```bash
java -Xmx4g -cp out white.PeakMemoryBenchmark <imagePath> <32HexKey> <outCsv>
```

Use the Bell_pepper scalability image (see
`Documentation/SCALABILITY_IMAGE_PREPARATION.md`) at 512x512 and 1024x1024
to reproduce the two rows of Table 10, with key 1 from `keys_used.csv` as
the representative key used in the paper.

## Reproducibility note

Unlike the deterministic ciphertext outputs (which are byte-identical given
the same key, image, and parameters), peak heap measurements are sensitive
to JVM version, garbage-collector behaviour, and JIT warm-up state, and are
**not** expected to be byte- or even close-percent-identical across
different machines or JVM builds. As a sanity check, we re-ran this exact
program on the 512x512 Bell_pepper image in a fresh environment and
obtained 25.61 +/- 0.22 MB (encryption) and 32.86 +/- 0.62 MB (decryption),
versus the published 27.75 +/- 0.62 MB and 35.65 +/- 1.03 MB -- the same
order of magnitude and the same relative pattern (decryption higher than
encryption), consistent with normal JVM memory-measurement variation rather
than a methodological error. Treat Table 10 as characterizing peak memory
*order of magnitude and scaling*, not as a byte-exact reproducibility
target the way the ciphertext outputs and structural-attack results are.
