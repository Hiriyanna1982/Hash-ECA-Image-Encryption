package lvrca;

/**
 * Port of key_to_bit / bit_to_val / bit_to_int / randNumCreate / derandNumCreate
 * from the authors' reference implementation (encrypt.cpp / decrypt.cpp,
 * https://github.com/NEUboy/encode_RCA). Ported line-for-line; no attempt is
 * made to "fix" anything, including the apparent quirk that chaos[i][j] and
 * chaos[i][j+1] are both assigned the SAME xi value (not xi,yi) for j<8, and
 * the fact that the mu-formula for round==0 vs round!=0 is swapped between
 * randNumCreate (encrypt) and derandNumCreate (decrypt) -- this is exactly
 * what the reference code does and is required for a correct round trip.
 */
public final class KeyChaos {
    private static final double PI = 3.1415926536; // matches the literal in the C++ source exactly

    private KeyChaos() {}

    /** key: exactly 64 uppercase hex characters (256 bits). */
    static byte[] keyToBit(String key) {
        if (key.length() < 64) throw new IllegalArgumentException("key must be at least 64 hex chars");
        byte[] bits = new byte[256];
        for (int i = 0; i < 64; i++) {
            char c = key.charAt(i);
            int digit = (c >= '0' && c <= '9') ? (c - 48) : (c - 55); // matches C++: '0'-'9' or 'A'-'F'
            int temp = 0x08;
            for (int j = 0; j < 4; j++) {
                bits[i * 4 + j] = (byte) (((digit & temp) == temp) ? 1 : 0);
                temp = temp >> 1;
            }
        }
        return bits;
    }

    static double bitToVal(byte[] bits, int start, int stop) {
        double val = 0;
        for (int i = start; i < stop; i++) {
            val += bits[i] * Math.pow(0.5, i - start + 1);
        }
        return val;
    }

    /** Returns the unsigned 32-bit value as a Java long in [0, 2^32-1]. */
    static long bitToInt(byte[] bits, int start, int stop) {
        long rec = 0;
        for (int i = start; i < stop; i++) {
            rec = (rec << 1) + bits[i];
        }
        return rec & 0xFFFFFFFFL;
    }

    private static final long MASK32 = 0xFFFFFFFFL;

    /**
     * Shared 2D-LSCM state; xi/yi/theta persist across the burn-in and the
     * per-block generation loop, matching the C++ local variables.
     */
    private static final class State {
        double xi, yi, theta;
    }

    private static State initState(String key, int round, boolean encryptSide) {
        byte[] bits = keyToBit(key);
        State s = new State();
        s.xi = bitToVal(bits, 0, 32) * bitToVal(bits, 32, 64);
        s.yi = bitToVal(bits, 64, 96) * bitToVal(bits, 96, 128);
        double theta0 = bitToVal(bits, 128, 160) * bitToVal(bits, 160, 192);
        long alpha = bitToInt(bits, 192, 224);
        long beta = bitToInt(bits, 224, 256);
        long mu;
        if (encryptSide) {
            mu = (round == 0) ? ((alpha + beta) & MASK32) : ((alpha ^ beta) & MASK32);
        } else {
            // derandNumCreate swaps the round==0/round!=0 branches relative to randNumCreate.
            mu = (round == 0) ? ((alpha ^ beta) & MASK32) : ((alpha + beta) & MASK32);
        }
        double theta = theta0 * (double) mu;
        theta = theta - Math.floor(theta);
        s.theta = theta;

        for (int i = 0; i < 30; i++) {
            step(s);
        }
        return s;
    }

    private static void step(State s) {
        double xNew = Math.sin(PI * ((4 * s.theta * s.xi * (1 - s.xi)) + (1 - s.theta) * Math.sin(PI * s.yi)));
        double yNew = Math.sin(PI * ((4 * s.theta * s.yi * (1 - s.yi)) + (1 - s.theta) * Math.sin(PI * xNew)));
        s.xi = xNew;
        s.yi = yNew;
    }

    /** Encrypt-side chaos generation (randNumCreate). chaos: double[length][9]. */
    public static void randNumCreate(double[][] chaos, int length, String key, int round) {
        State s = initState(key, round, true);
        fill(chaos, length, s);
    }

    /** Decrypt-side chaos generation (derandNumCreate). chaos: double[length][9]. */
    public static void derandNumCreate(double[][] chaos, int length, String key, int round) {
        State s = initState(key, round, false);
        fill(chaos, length, s);
    }

    private static void fill(double[][] chaos, int length, State s) {
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < 10; j += 2) {
                step(s);
                if (j < 8) {
                    chaos[i][j] = s.xi;
                    chaos[i][j + 1] = s.xi; // matches the reference code exactly (both slots = xi)
                } else {
                    chaos[i][j] = (s.xi + s.yi) / 2.0;
                }
            }
        }
    }
}
