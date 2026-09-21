package lvrca;

/**
 * RGB extension (the paper and reference code target 8-bit
 * grayscale only): apply ChannelCipher independently to R, then G, then B,
 * using the same published/reference 256-bit key for all three channels,
 * with the 2D-LSCM chaos generator and all cipher state freshly
 * reinitialized at the start of each channel (ChannelCipher.encrypt/decrypt
 * takes a fresh byte[][] and re-derives everything from `key`, so this reset
 * is automatic/structural -- each channel call shares no state with the
 * others). This behavior is documented in Lv2023_ASSUMPTIONS.md.
 */
public final class RgbCipher {
    private RgbCipher() {}

    public static byte[][] encryptColor(byte[][] planes /* [0]=R,[1]=G,[2]=B, row-major m*n */, int m, int n, String key) {
        byte[][] out = new byte[3][];
        for (int c = 0; c < 3; c++) {
            byte[][] buf = to2D(planes[c], m, n);
            ChannelCipher.encrypt(buf, m, n, key);
            out[c] = to1D(buf, m, n);
        }
        return out;
    }

    public static byte[][] decryptColor(byte[][] planes, int m, int n, String key) {
        byte[][] out = new byte[3][];
        for (int c = 0; c < 3; c++) {
            byte[][] buf = to2D(planes[c], m, n);
            ChannelCipher.decrypt(buf, m, n, key);
            out[c] = to1D(buf, m, n);
        }
        return out;
    }

    private static byte[][] to2D(byte[] flat, int m, int n) {
        byte[][] buf = new byte[m][n];
        for (int r = 0; r < m; r++) {
            System.arraycopy(flat, r * n, buf[r], 0, n);
        }
        return buf;
    }

    private static byte[] to1D(byte[][] buf, int m, int n) {
        byte[] flat = new byte[m * n];
        for (int r = 0; r < m; r++) {
            System.arraycopy(buf[r], 0, flat, r * n, n);
        }
        return flat;
    }
}
