package dong;

import java.util.Arrays;

/**
 * Sec. 4.1.2/4.1.3: encryption and decryption once the key seed Ks is known.
 * Implemented directly for the color-image case (Sec. 4.1.3: the entire
 * R,G,B data is treated as ONE combined M*N*3-length vector), since the benchmark images are RGB.
 *
 * Documented implementation decisions specific to this stage:
 * - Eq. (21) uses denominator 2^48-1 (confirmed distinct from Eq. 14's
 *   2^48 by exact PDF text extraction).
 * - Eq. (21)'s own text says r1,r2 are obtained from Nr1,Nr2 "as
 *   Step 4 in Sect. 4.1.1" -- and Step 4 includes the Eq. (16) Nr1==Nr2
 *   collision override. So the same override IS applied here (previously
 *   this implementation incorrectly skipped it on the theory that Eq. 21
 *   didn't restate it in-line; the paper does cross-reference it).
 * - S0 = Ks (the full 256-bit key seed) for this stage's PRCML-HECA model.
 * - A-Dong-20 burn-in is applied to all 256 lattices AFTER the 255-step
 *   lattice-seeding chain (Sec. 4.1.2 Step 2), consistent with every other
 *   PRCML-HECA usage in this cipher.
 * - Eq. (23)'s floor(ks_i(j)*256) requires ks_i(j) in [0,1); the model's
 *   native output range is [0,2pi) (Eq. 7), so ks_i(j) is normalized by
 *   dividing by 2*pi first -- consistent with the paper's own statement
 *   (Sec. 3.2) that model outputs are "normalized from the interval
 *   [0,2pi] to [0,1]", and necessary for Eq. (23)'s stated result domain
 *   {0,...,255} to be reachable at all. Sorting (Eq. 25, used for both the
 *   permutation and the S-box) is invariant to this scaling, so it is not
 *   applied there.
 * - Permutation/S-box use a stable ascending sort (Eq. 25), ties broken by
 *   original index order (matches the paper's own worked example).
 */
public final class CipherStage {
    private CipherStage() {}

    private static final double POW2_48_M1 = Math.pow(2, 48) - 1.0;
    private static final int NPRIME = 200;

    private static final class Keystream {
        int[] kxor1, kxor2; // byte values 0..255, length n3 each
        int[] permI;        // length n3: permI[k] = original index of k-th smallest Kp value
        int[] sbox;         // length 256
        int[] invSbox;      // length 256
    }

    private static int toByte(double raw) {
        double norm = raw / Csm.TWO_PI; // [0,2pi) -> [0,1)
        int v = (int) Math.floor(norm * 256.0);
        if (v < 0) v = 0;
        if (v > 255) v = 255;
        return v;
    }

    private static Keystream deriveKeystream(boolean[] Ks, int n3) {
        if (Ks.length != 256) throw new IllegalArgumentException("Ks must be 256 bits");
        if (n3 % 256 != 0) // n = (n3/256)*3 must be an integer; n is then automatically a multiple of 3
            throw new IllegalArgumentException("plaintext vector length must be a multiple of 256");

        // Step 1
        boolean[] Kx = BitUtil.slice1(Ks, 1, 48);
        boolean[] Ky = BitUtil.slice1(Ks, 49, 96);
        boolean[] Kk = BitUtil.slice1(Ks, 97, 144);
        boolean[] Ke = BitUtil.slice1(Ks, 145, 192);
        boolean[] Kr1 = BitUtil.slice1(Ks, 193, 224);
        boolean[] Kr2 = BitUtil.slice1(Ks, 225, 256);

        // Step 2 (Eq. 21) -- denominator 2^48 - 1
        double x0Lat1 = (BitUtil.bin2dec(Kx) / POW2_48_M1) * Csm.TWO_PI;
        double y0Lat1 = (BitUtil.bin2dec(Ky) / POW2_48_M1) * Csm.TWO_PI;
        double K = 8.0 + (BitUtil.bin2dec(Kk) / POW2_48_M1) * 8.0;
        double eps = (BitUtil.bin2dec(Ke) / POW2_48_M1) * 0.5;
        int Nr1 = (int) (BitUtil.bin2dec(Kr1) % 34) + 1;
        int Nr2 = (int) (BitUtil.bin2dec(Kr2) % 34) + 1;
        if (Nr1 == Nr2) {
            Nr2 = (Nr1 + 1) % 34 + 1; // Eq. 16 collision override, per Step 4 cross-reference (see class-level note)
        }

        double[][] chain = ModelInit.chain255(x0Lat1, y0Lat1, K);
        double[][] burned = ModelInit.burnIn20(chain[0], chain[1], K); // A-Dong-20

        CoupledLattice lattice = new CoupledLattice(burned[0], burned[1], Ks, K, eps, Nr1, Nr2);

        // Step 3: n' burn-in iterations (discarded), then n+1 measured iterations captured as keystream.
        int n = (n3 / 256) * 3; // Eq. 22/35 for color: n = (M*N*3/256)*3
        lattice.iterate(NPRIME);

        double[][] ks = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            lattice.stepOnce();
            ks[i] = lattice.getY().clone();
        }

        // Step 4: divide into 4 parts
        int third = n / 3;
        double[] kxor1Raw = new double[n3];
        double[] kpRaw = new double[n3];
        double[] ksboxRaw = ks[2 * third]; // single 256-vector
        double[] kxor2Raw = new double[n3];

        for (int b = 0; b < third; b++) System.arraycopy(ks[b], 0, kxor1Raw, b * 256, 256);
        for (int b = 0; b < third; b++) System.arraycopy(ks[third + b], 0, kpRaw, b * 256, 256);
        for (int b = 0; b < third; b++) System.arraycopy(ks[2 * third + 1 + b], 0, kxor2Raw, b * 256, 256);

        Keystream out = new Keystream();

        // Eq. 23 byte conversion
        out.kxor1 = new int[n3];
        out.kxor2 = new int[n3];
        for (int k = 0; k < n3; k++) {
            out.kxor1[k] = toByte(kxor1Raw[k]);
            out.kxor2[k] = toByte(kxor2Raw[k]);
        }

        // Eq. 25/26 permutation index (stable ascending sort of Kp; scale-invariant, no /2pi needed)
        Integer[] idx = new Integer[n3];
        for (int k = 0; k < n3; k++) idx[k] = k;
        Arrays.sort(idx, (a, c) -> Double.compare(kpRaw[a], kpRaw[c])); // stable (Timsort)
        out.permI = new int[n3];
        for (int k = 0; k < n3; k++) out.permI[k] = idx[k];

        // Eq. 27 S-box (stable ascending sort of Ksbox, 256 elements)
        Integer[] sidx = new Integer[256];
        for (int m = 0; m < 256; m++) sidx[m] = m;
        Arrays.sort(sidx, (a, c) -> Double.compare(ksboxRaw[a], ksboxRaw[c]));
        out.sbox = new int[256];
        for (int m = 0; m < 256; m++) out.sbox[m] = sidx[m];
        out.invSbox = new int[256];
        for (int m = 0; m < 256; m++) out.invSbox[out.sbox[m]] = m;

        return out;
    }

    /** Encrypts a flattened plaintext vector (column-major RGB, values 0..255). */
    public static int[] encrypt(boolean[] Ks, int[] plainVector) {
        int n3 = plainVector.length;
        Keystream ksm = deriveKeystream(Ks, n3);

        int[] c1 = new int[n3];
        for (int k = 0; k < n3; k++) c1[k] = ksm.kxor1[k] ^ plainVector[k]; // Eq. 24

        int[] c2 = new int[n3];
        for (int k = 0; k < n3; k++) c2[k] = c1[ksm.permI[k]]; // Eq. 26

        int[] c3 = new int[n3];
        for (int k = 0; k < n3; k++) c3[k] = ksm.sbox[c2[k]]; // Eq. 28

        int[] cOut = new int[n3];
        for (int k = 0; k < n3; k++) cOut[k] = ksm.kxor2[k] ^ c3[k]; // Eq. 29

        return cOut;
    }

    /** Decrypts a flattened ciphertext vector (column-major RGB, values 0..255). */
    public static int[] decrypt(boolean[] Ks, int[] cipherVector) {
        int n3 = cipherVector.length;
        Keystream ksm = deriveKeystream(Ks, n3);

        int[] c3 = new int[n3];
        for (int k = 0; k < n3; k++) c3[k] = ksm.kxor2[k] ^ cipherVector[k]; // Eq. 30

        int[] c2 = new int[n3];
        for (int k = 0; k < n3; k++) c2[k] = ksm.invSbox[c3[k]]; // Eq. 32

        int[] c1 = new int[n3];
        for (int k = 0; k < n3; k++) c1[ksm.permI[k]] = c2[k]; // Eq. 33 (inverse scatter)

        int[] plain = new int[n3];
        for (int k = 0; k < n3; k++) plain[k] = ksm.kxor1[k] ^ c1[k]; // Eq. 34

        return plain;
    }
}
