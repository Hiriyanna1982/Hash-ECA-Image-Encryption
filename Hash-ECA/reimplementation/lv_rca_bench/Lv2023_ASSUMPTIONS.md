# Assumptions — Lv et al. (2023)

Only details not specified for RGB use in the paper/reference implementation are listed here. The published method and authors' reference code are otherwise followed directly.

## A1 — RGB extension

The published scheme and reference implementation operate on 8-bit grayscale images and do not define an RGB extension.

**Used:** apply the complete grayscale cipher independently to the R, G, and B channels.

Channels are processed in the order:

```text
R -> G -> B
```

## A2 — Key use across channels

For each image-key trial, the same 256-bit key from `lv_keys.csv` is applied independently to the R, G, and B channels.

The 10 keys listed in `lv_keys.csv` are used for the 10-key statistical evaluation. Runtime is evaluated separately using the fixed published/reference key implemented by the single-key benchmark.

## A3 — Cipher state across channels

Each RGB channel is treated as an independent grayscale encryption.

**Used:** the chaotic generator and cipher state are reinitialized from the selected key at the beginning of each channel. No state is carried from one channel to the next.

## A4 — Image dimensions

The published method operates on 8×8 blocks.

All benchmark images are 512×512, so both dimensions are already divisible by 8 and no padding is required.

## Implementation note

The Java implementation is a direct port of the authors' published C++ reference implementation, including its key schedule, 2D-LSCM generation, pretreatment, block permutation, and reversible cellular-automaton diffusion.

No algorithmic optimization or replacement of the reference sorting/evolution procedures was introduced for the benchmark.

Encryption and decryption times therefore represent the complete three-channel RGB processing time.

The final implementation passed exact encryption/decryption round-trip verification for all 10 RGB BMP images.
