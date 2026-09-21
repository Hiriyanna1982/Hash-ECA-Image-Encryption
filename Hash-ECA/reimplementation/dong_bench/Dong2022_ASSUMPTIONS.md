# Assumptions — Dong et al. (2022)

Only details not uniquely specified in the paper, or source-paper inconsistencies that required a fixed implementation choice, are listed here. These choices were fixed before final evaluation.

## A1 — Benchmark timestamp

The key-seed generation depends on the encryption timestamp, but the paper does not prescribe a fixed benchmark timestamp.

**Used:** `20260101000000` in `YYYYMMDDHHMMSS` form.

## A2 — RGB serialization order

The paper does not uniquely specify how an RGB image is serialized into the plaintext vector.

**Used:** MATLAB-style column-major order:

```text
index = row + column*M + channel*M*N
```

with the complete R plane followed by G and B.

## A3 — Coupled-neighbor perturbation term

In Eq. (7), the perturbation term is written as a scalar although the model state contains two components.

**Used:** the same scalar perturbation term is added to both the `x` and `y` components.

## A4 — 20-iteration CSM preprocessing

Section 3.1 specifies a 20-iteration CSM preprocessing step, while the encryption procedure does not restate it for every later use of the model.

**Used:** the 20-iteration preprocessing is applied consistently to each fresh `(x0,y0)` pair in:
- key-seed per-block generation;
- the series-wound model; and
- the final cipher-stage model.

## A5 — Series-wound model iteration count

Figure 16 shows a single chain of `q` boxes, each iterated once. The complexity discussion in Section 4.3.2 implies a `2q` contribution from this stage.

**Used:** a single pass of `q` boxes, following the literal wiring in Figure 16.

This source-paper inconsistency is disclosed rather than silently resolved.

## A6 — Key-seed summation in Eq. (19)

Eq. (19) is typeset using unprimed `k_i`, while the surrounding text states that the series-wound outputs `k_i'` are obtained immediately before the key seed is calculated.

**Used:** the summation is performed over the primed series-wound outputs `k_i'`.

No `2π` normalization is applied before the `mod 1` operation in Eq. (19).

## A7 — Byte conversion in Eq. (23)

The PRCML model produces values in `[0,2π)`, whereas Eq. (23) requires a value in `[0,1)` before multiplication by 256.

**Used:**

```text
floor((value / (2*pi)) * 256)
```

before the required byte conversion.

## A8 — Rule-number collision handling

The text following Eq. (21) obtains the two HECA rules using Step 4 of Section 4.1.1, which includes the Eq. (16) collision rule.

**Used:** the `Nr1 == Nr2` collision override is applied in both key-seed generation and the final cipher stage.

## A9 — Stable sorting

The permutation and S-box constructions require sorting derived real-valued sequences, but tie handling is not stated explicitly.

**Used:** stable ascending sort, with equal values retaining their original index order.

## Implementation note

Only the color-image path was implemented because the matched benchmark uses 512×512, 24-bit RGB BMP images.

The final implementation passed exact encryption/decryption round-trip verification for all 10 benchmark images.
