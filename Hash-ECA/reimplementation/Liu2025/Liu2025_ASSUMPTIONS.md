# Assumptions — Liu et al. (2025)

Only details not uniquely specified in the paper are listed here. These choices were fixed before final evaluation.

## A1 — 512-bit plaintext hash

The paper specifies a 512-bit plaintext hash but does not name the algorithm.

**Used:** SHA-512.

Each RGB channel is hashed independently.

## A2 — Chaotic transient

The transient length `z` is not numerically specified.

**Used:** `z = 500`.

## A3 — Real-to-integer conversion

The paper scales chaotic values by `10^14` before modulo operations but does not state the exact real-to-integer conversion.

**Used:**

```text
floor(abs(x) * 10^14)
```

before the required modulo operation.

## A4 — Cross-ring Josephus indexing

The paper does not fully specify the indexing/counting convention required to reproduce every elimination in the worked example.

**Used deterministic convention:**

```text
next_ring = (current_ring_pointer + a_i) mod active_ring_count
position  = (current_position_reference + b_i) mod current_ring_size
```

The position reference is retained after deletion and empty rings are removed.

A systematic check of plausible indexing conventions did not reproduce the complete published worked example; therefore this convention is reported explicitly as an implementation assumption.

## Implementation note

For CA diffusion, the next row inherits the previous row's **last real ciphertext element** as required by Eq. (13). The same interpretation is used in encryption and decryption.

The final implementation passed exact encryption/decryption round-trip verification for all 10 RGB BMP images.
