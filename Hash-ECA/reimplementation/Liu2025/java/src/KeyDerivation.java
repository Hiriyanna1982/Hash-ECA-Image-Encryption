package liu;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Key-sequence generation (paper Section III-C(1), Eq. 6) with the documented
 * implementation assumptions:
 *   A1: 512-bit hash = SHA-512.
 *   A2: transient length z = 500.
 *   A3: real->integer discretization = floor(|x| * 1e14) mod q.
 * Eq. 6 fix: the paper's four update lines print a bare "x0"/"y0" but the
 * secret key is stated (Sec III-C intro and the experimental section) as
 * {x0^1,y0^1,x0^2,y0^2,a,b}. We use x0^1/y0^1 for the first two lines
 * (producing x0',y0') and x0^2/y0^2 for the last two (producing x0'',y0'').
 */
public final class KeyDerivation {

    public static final int Z = 500; // A2

    public final double x0p, y0p, x0pp, y0pp; // x0', y0', x0'', y0''
    public final int[] Uprime;  // inter-ring step sequence, values in [1,m]
    public final int[] Vprime;  // intra-ring step sequence, values in [1,n]
    public final int[] Wprime;  // CA rule selector, values in [1,6]
    public final int[] Rprime;  // diffusion pad, values in [0,255]

    /** Encryption side: hash is computed from the plaintext channel bytes. */
    public static KeyDerivation fromPlaintext(byte[] plaintextChannelBytes,
                                               double x0_1, double y0_1, double x0_2, double y0_2,
                                               double a, double b, int m, int n) {
        return new KeyDerivation(sha512(plaintextChannelBytes), x0_1, y0_1, x0_2, y0_2, a, b, m, n);
    }

    /**
     * Decryption side: the 64-byte hash H is already known (transmitted
     * separately per Sec III-D - decryption cannot re-hash a plaintext it
     * doesn't have yet).
     */
    public static KeyDerivation fromHash(byte[] hash64Bytes,
                                          double x0_1, double y0_1, double x0_2, double y0_2,
                                          double a, double b, int m, int n) {
        return new KeyDerivation(hash64Bytes, x0_1, y0_1, x0_2, y0_2, a, b, m, n);
    }

    private KeyDerivation(byte[] hash64Bytes,
                           double x0_1, double y0_1, double x0_2, double y0_2,
                           double a, double b, int m, int n) {
        int[] h = new int[64];
        for (int i = 0; i < 64; i++) h[i] = hash64Bytes[i] & 0xFF;

        this.x0p  = updated(sumRange(h, 1, 8)  ^ sumRange(h, 9, 16), x0_1);
        this.y0p  = updated(sumRange(h, 17, 24) ^ sumRange(h, 25, 32), y0_1);
        this.x0pp = updated(sumRange(h, 33, 40) ^ sumRange(h, 41, 48), x0_2);
        this.y0pp = updated(sumRange(h, 49, 56) ^ sumRange(h, 57, 64), y0_2);

        int mn = m * n;

        double[][] uv = CICM.generate(x0p, y0p, a, b, Z, mn);
        this.Uprime = discretize(uv[0], m, 1);
        this.Vprime = discretize(uv[1], n, 1);

        double[][] wr = CICM.generate(x0pp, y0pp, a, b, Z, mn);
        this.Wprime = discretize(wr[0], 6, 1);
        this.Rprime = discretize(wr[1], 256, 0);
    }

    /** 1-indexed inclusive range sum over h[1..64] stored 0-indexed in h[0..63]. */
    private static int sumRange(int[] h, int fromOneIndexed, int toOneIndexedInclusive) {
        int s = 0;
        for (int i = fromOneIndexed; i <= toOneIndexedInclusive; i++) s += h[i - 1];
        return s;
    }

    private static double updated(int xorSum, double x0) {
        double v = (xorSum / 1024.0) + x0; // /2^10
        return v - Math.floor(v);          // mod 1 (true fractional part, non-negative)
    }

    /** A3: floor(|x|*1e14) mod q, then + offset (0 or 1). */
    private static int[] discretize(double[] xs, int q, int offset) {
        int[] out = new int[xs.length];
        for (int i = 0; i < xs.length; i++) {
            double scaled = Math.abs(xs[i]) * 1e14;
            long floored = (long) Math.floor(scaled);
            long m = floored % q; // floored >= 0, so already non-negative
            out[i] = (int) (m + offset);
        }
        return out;
    }

    public static byte[] sha512(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512"); // A1
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
