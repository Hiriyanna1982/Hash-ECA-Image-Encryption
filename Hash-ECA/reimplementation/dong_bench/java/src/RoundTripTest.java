package dong;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Small synthetic-image correctness check (32x32 RGB => M*N*3=3072, divisible
 * by 512 so key-seed generation's block count q=6 is exact, and n=36 for the
 * cipher stage) before running the full 512x512x10-image benchmark.
 */
public final class RoundTripTest {
    public static void main(String[] args) {
        int M = 32, N = 32;
        Random rnd = new Random(42);
        BufferedImage img = new BufferedImage(N, M, BufferedImage.TYPE_INT_RGB);
        for (int c = 0; c < N; c++)
            for (int r = 0; r < M; r++)
                img.setRGB(c, r, (rnd.nextInt(256) << 16) | (rnd.nextInt(256) << 8) | rnd.nextInt(256));

        int[] plain = ImageVector.flattenRGB(img);
        System.out.println("n3 = " + plain.length);

        String keyHex = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"; // 256 bits
        boolean[] Ki = BitUtil.hexToBits(keyHex);
        long timestamp = 20260101000000L; // fixed representative timestamp (YYYYMMDDHHMMSS)

        long t0 = System.nanoTime();
        boolean[] Ks = KeySeedGenerator.generate(Ki, timestamp, plain);
        long t1 = System.nanoTime();
        System.out.printf("Key-seed generation: %.2f ms%n", (t1 - t0) / 1e6);

        StringBuilder sb = new StringBuilder();
        for (boolean b : Ks) sb.append(b ? '1' : '0');
        System.out.println("Ks = " + sb);

        long t2 = System.nanoTime();
        int[] cipher = CipherStage.encrypt(Ks, plain);
        long t3 = System.nanoTime();
        int[] decrypted = CipherStage.decrypt(Ks, cipher);
        long t4 = System.nanoTime();

        System.out.printf("Cipher-stage encrypt: %.2f ms, decrypt: %.2f ms%n", (t3 - t2) / 1e6, (t4 - t3) / 1e6);

        boolean ok = true;
        int mismatches = 0;
        for (int i = 0; i < plain.length; i++) {
            if (plain[i] != decrypted[i]) { ok = false; mismatches++; }
        }
        System.out.println("Round-trip OK: " + ok + " (mismatches=" + mismatches + ")");

        // Sanity: ciphertext should differ substantially from plaintext
        int sameAsPlain = 0;
        for (int i = 0; i < plain.length; i++) if (plain[i] == cipher[i]) sameAsPlain++;
        System.out.println("Cipher bytes equal to plaintext bytes: " + sameAsPlain + " / " + plain.length);

        if (!ok) {
            System.out.println("FAIL");
            System.exit(1);
        } else {
            System.out.println("PASS");
        }
    }
}
