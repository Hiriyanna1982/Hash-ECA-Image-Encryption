package dong;

/**
 * Bit-vector helpers matching the paper's 1-indexed, MSB-first binary
 * conventions (e.g. Ki(1:48), bin2dec(), dec2bin()).
 *
 * Internally all bit vectors are boolean[] with index 0 == the paper's
 * index 1 (i.e. 0-indexed, MSB-first: bits[0] is the most significant /
 * "first" bit of the vector).
 */
public final class BitUtil {
    private BitUtil() {}

    /** Parses a hex string into a bit array, MSB-first, 4 bits per hex digit. */
    public static boolean[] hexToBits(String hex) {
        boolean[] bits = new boolean[hex.length() * 4];
        for (int i = 0; i < hex.length(); i++) {
            int v = Character.digit(hex.charAt(i), 16);
            if (v < 0) throw new IllegalArgumentException("Bad hex digit: " + hex.charAt(i));
            for (int b = 0; b < 4; b++) {
                // MSB first within the nibble
                bits[i * 4 + b] = ((v >> (3 - b)) & 1) == 1;
            }
        }
        return bits;
    }

    /**
     * Slice using the paper's 1-indexed, inclusive convention: Ki(a:b) becomes
     * bits[a-1 .. b-1] inclusive, returned as a new array of length (b-a+1).
     */
    public static boolean[] slice1(boolean[] bits, int a1, int b1) {
        int len = b1 - a1 + 1;
        boolean[] out = new boolean[len];
        System.arraycopy(bits, a1 - 1, out, 0, len);
        return out;
    }

    /** bin2dec(): interprets bits[start..start+len) MSB-first as an unsigned integer. len <= 62. */
    public static long bin2dec(boolean[] bits, int start, int len) {
        long v = 0;
        for (int k = 0; k < len; k++) {
            v = (v << 1) | (bits[start + k] ? 1L : 0L);
        }
        return v;
    }

    /** bin2dec() over a full bit array. */
    public static long bin2dec(boolean[] bits) {
        return bin2dec(bits, 0, bits.length);
    }

    /** dec2bin(): converts a non-negative decimal value to an nBits-long MSB-first bit array. */
    public static boolean[] dec2bin(long value, int nBits) {
        boolean[] out = new boolean[nBits];
        for (int k = 0; k < nBits; k++) {
            int shift = nBits - 1 - k;
            out[k] = ((value >> shift) & 1L) == 1L;
        }
        return out;
    }

    /** Element-wise XOR of two equal-length bit arrays. */
    public static boolean[] xor(boolean[] a, boolean[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("length mismatch");
        boolean[] out = new boolean[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] ^ b[i];
        return out;
    }

    /** Concatenates bit arrays in order. */
    public static boolean[] concat(boolean[]... arrays) {
        int total = 0;
        for (boolean[] a : arrays) total += a.length;
        boolean[] out = new boolean[total];
        int pos = 0;
        for (boolean[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }
}
