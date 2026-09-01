package white;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public final class CipherUtil {
    private CipherUtil() {}

    public static final byte TAG_SCRAMBLE_INIT   = 0x11;
    public static final byte TAG_SCRAMBLE_BLOCK  = 0x12;
    public static final byte TAG_SCRAMBLE_COLUMN = 0x13;
    public static final byte TAG_SCRAMBLE_ROW    = 0x14;
    public static final byte TAG_SCRAMBLE_PIXEL  = 0x15;
    public static final byte TAG_SCRAMBLE_UPDATE = 0x16;

    public static final byte TAG_DIFFUSION_INIT  = 0x21;
    public static final byte TAG_DIFFUSION_ROUND = 0x22;
    public static final byte TAG_DIFFUSION_PIXEL = 0x23;

    public static byte[] keyBytesFromHex(String hex) {
        if (hex == null || hex.length() != 32)
            throw new IllegalArgumentException("Key must be exactly 32 hex characters (128 bits).");

        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            int hi = Character.digit(hex.charAt(2*i), 16);
            int lo = Character.digit(hex.charAt(2*i+1), 16);
            if (hi < 0 || lo < 0)
                throw new IllegalArgumentException("Invalid hexadecimal key.");
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }

    public static byte[] be32(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    public static byte[] sha256(byte[]... parts) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (byte[] p : parts) md.update(p);
        return md.digest();
    }

    public static int mod256(int v) {
        return ((v % 256) + 256) % 256;
    }

    public static int positiveIntFromFirst4Bytes(byte[] d) {
        int v = ((d[0] & 0xff) << 24)
              | ((d[1] & 0xff) << 16)
              | ((d[2] & 0xff) << 8)
              |  (d[3] & 0xff);
        return v & 0x7fffffff;
    }
}
