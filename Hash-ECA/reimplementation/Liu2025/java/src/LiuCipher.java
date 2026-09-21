package liu;

/**
 * Orchestrates the full scheme per channel (Section III-C/D): key
 * generation -> cross-ring Josephus scrambling -> CA diffusion, and its
 * inverse. Color images are handled as three independent channel
 * encryptions per the paper ("each component is then encrypted
 * individually... the hash value for each component needs to be computed
 * independently").
 */
public final class LiuCipher {

    public static final double A = 7, B = 5;
    public static final double X0_1 = 0.3, Y0_1 = 0.5, X0_2 = 0.6, Y0_2 = 0.8;

    public static final class EncryptedChannel {
        public final byte[] cipher;
        public final byte[] hash; // H - must be carried alongside the ciphertext for decryption
        EncryptedChannel(byte[] cipher, byte[] hash) { this.cipher = cipher; this.hash = hash; }
    }

    public static EncryptedChannel encryptChannel(byte[] plane, int m, int n) {
        return encryptChannel(plane, m, n, X0_1, Y0_1, X0_2, Y0_2, A, B);
    }

    /** Overload accepting an explicit secret key {x0_1,y0_1,x0_2,y0_2,a,b},
     * for the 10-key statistical comparison. Identical logic to the
     * fixed-key overload above; only the key values are parameterized. */
    public static EncryptedChannel encryptChannel(byte[] plane, int m, int n,
                                                   double x0_1, double y0_1, double x0_2, double y0_2,
                                                   double a, double b) {
        byte[] hash = KeyDerivation.sha512(plane);
        KeyDerivation kd = KeyDerivation.fromHash(hash, x0_1, y0_1, x0_2, y0_2, a, b, m, n);
        int[] perm = Josephus.permutation(m, n, kd.Uprime, kd.Vprime);
        byte[] scrambled = Josephus.scramble(plane, perm);
        byte[] cipher = CADiffusion.diffuse(scrambled, kd.Wprime, kd.Rprime, m, n);
        return new EncryptedChannel(cipher, hash);
    }

    public static byte[] decryptChannel(byte[] cipher, byte[] hash, int m, int n) {
        KeyDerivation kd = KeyDerivation.fromHash(hash, X0_1, Y0_1, X0_2, Y0_2, A, B, m, n);
        byte[] scrambled = CADiffusion.undiffuse(cipher, kd.Wprime, kd.Rprime, m, n);
        int[] perm = Josephus.permutation(m, n, kd.Uprime, kd.Vprime);
        return Josephus.descramble(scrambled, perm);
    }

    /** planes[0..2] = R,G,B, each m*n row-major bytes. */
    public static EncryptedChannel[] encryptColor(byte[][] planes, int m, int n) {
        EncryptedChannel[] out = new EncryptedChannel[3];
        for (int c = 0; c < 3; c++) out[c] = encryptChannel(planes[c], m, n);
        return out;
    }

    /** Overload accepting an explicit secret key for the 10-key statistical comparison. */
    public static EncryptedChannel[] encryptColor(byte[][] planes, int m, int n,
                                                   double x0_1, double y0_1, double x0_2, double y0_2,
                                                   double a, double b) {
        EncryptedChannel[] out = new EncryptedChannel[3];
        for (int c = 0; c < 3; c++) out[c] = encryptChannel(planes[c], m, n, x0_1, y0_1, x0_2, y0_2, a, b);
        return out;
    }

    public static byte[][] decryptColor(EncryptedChannel[] enc, int m, int n) {
        byte[][] out = new byte[3][];
        for (int c = 0; c < 3; c++) out[c] = decryptChannel(enc[c].cipher, enc[c].hash, m, n);
        return out;
    }
}
