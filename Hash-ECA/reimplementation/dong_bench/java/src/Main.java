package dong;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import javax.imageio.ImageIO;

/**
 * Single-key runtime and round-trip benchmark for the Dong et al.
 * PRCML-HECA reimplementation.
 *
 * The benchmark uses the published representative 256-bit key, a fixed
 * documented timestamp, the controlled differential perturbation at
 * zero-based pixel (255,255), and 5 warm-up plus 20 measured runs per image.
 *
 * Encryption timing includes plaintext-dependent key-seed generation and
 * cipher-stage encryption. Decryption timing covers cipher-stage decryption,
 * for which the derived key seed is an input as specified by the source
 * algorithm.
 */
public final class Main {

    static final String KEY_HEX = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"; // paper's own published key, repeated 4x = 256 bits
    static final long TIMESTAMP = 20260101000000L; // fixed representative timestamp (YYYYMMDDHHMMSS), documented assumption

    static final int PERTURB_ROW = 255, PERTURB_COL = 255;
    static final int WARMUP_RUNS = 5;
    static final int MEASURED_RUNS = 20;

    public static void main(String[] args) throws Exception {
        String inDir = args.length > 0 ? args[0] : "images_locked";
        String outDir = args.length > 1 ? args[1] : "results";
        new File(outDir).mkdirs();

        String[] names = {"Airplane","Baboon","Barbara","Boats","House","Lena","Monarch","Pepper","Sailboat","Tiffany"};
        boolean[] Ki = BitUtil.hexToBits(KEY_HEX);

        PrintWriter summaryLog = new PrintWriter(new File(outDir, "timing_ms.csv"));
        summaryLog.println("image,encrypt_ms_mean,encrypt_ms_std,decrypt_ms_mean,decrypt_ms_std,keyseed_ms_mean,cipherstage_enc_ms_mean,roundtrip_ok,mismatched_bytes,warmup_runs,measured_runs");

        PrintWriter rawLog = new PrintWriter(new File(outDir, "timing_runs_ms.csv"));
        rawLog.println("image,run_index,encrypt_ms,decrypt_ms,keyseed_ms,cipherstage_enc_ms");

        for (String name : names) {
            long imgStart = System.currentTimeMillis();
            String path = inDir + "/" + name + ".bmp";
            BufferedImage original = ImageIO.read(new File(path));
            if (original == null) throw new IllegalArgumentException("Unreadable: " + path);
            int[] plain = ImageVector.flattenRGB(original);
            int M = original.getHeight(), N = original.getWidth();

            // ---- warm-up: discard timings ----
            for (int w = 0; w < WARMUP_RUNS; w++) {
                boolean[] Ks = KeySeedGenerator.generate(Ki, TIMESTAMP, plain);
                int[] cipher = CipherStage.encrypt(Ks, plain);
                CipherStage.decrypt(Ks, cipher);
            }

            double[] encMsRuns = new double[MEASURED_RUNS];
            double[] decMsRuns = new double[MEASURED_RUNS];
            double[] keyseedMsRuns = new double[MEASURED_RUNS];
            double[] cipherEncMsRuns = new double[MEASURED_RUNS];
            boolean roundtripOk = true;
            long mismatches = 0;
            int[] cipherVec = null;

            for (int r = 0; r < MEASURED_RUNS; r++) {
                long t0 = System.nanoTime();
                boolean[] Ks = KeySeedGenerator.generate(Ki, TIMESTAMP, plain);
                long t1 = System.nanoTime();
                int[] cipher = CipherStage.encrypt(Ks, plain);
                long t2 = System.nanoTime();
                int[] dec = CipherStage.decrypt(Ks, cipher);
                long t3 = System.nanoTime();

                keyseedMsRuns[r] = (t1 - t0) / 1e6;
                cipherEncMsRuns[r] = (t2 - t1) / 1e6;
                encMsRuns[r] = (t2 - t0) / 1e6; // encrypt = keyseed + cipher-stage encrypt
                decMsRuns[r] = (t3 - t2) / 1e6; // decrypt = cipher-stage decrypt only (Ks given)

                rawLog.printf("%s,%d,%.3f,%.3f,%.3f,%.3f%n", name, r, encMsRuns[r], decMsRuns[r], keyseedMsRuns[r], cipherEncMsRuns[r]);
                rawLog.flush();

                for (int i = 0; i < plain.length; i++) {
                    if (plain[i] != dec[i]) { roundtripOk = false; mismatches++; }
                }
                cipherVec = cipher;
            }

            double encMean = mean(encMsRuns), encStd = std(encMsRuns, encMean);
            double decMean = mean(decMsRuns), decStd = std(decMsRuns, decMean);
            double keyseedMean = mean(keyseedMsRuns);
            double cipherEncMean = mean(cipherEncMsRuns);

            System.out.printf("%-10s encrypt=%.2f+/-%.2fms (keyseed=%.2f cipher=%.2f) decrypt=%.2f+/-%.2fms roundtrip=%s mismatches=%d [%.1fs]%n",
                    name, encMean, encStd, keyseedMean, cipherEncMean, decMean, decStd, roundtripOk ? "OK" : "FAIL", mismatches,
                    (System.currentTimeMillis() - imgStart) / 1000.0);
            summaryLog.printf("%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%d,%d,%d%n",
                    name, encMean, encStd, decMean, decStd, keyseedMean, cipherEncMean, roundtripOk, mismatches, WARMUP_RUNS, MEASURED_RUNS);
            summaryLog.flush();

            ImageIO.write(ImageVector.unflattenRGB(cipherVec, M, N), "bmp", new File(outDir + "/" + name + "_cipher.bmp"));

            // ---- differential attack pair: C1 = E(P), C2 = E(P with one pixel perturbed) ----
            BufferedImage perturbed = copyImage(original);
            int rgb = perturbed.getRGB(PERTURB_COL, PERTURB_ROW) & 0xffffff;
            int pr = (rgb>>16)&0xff, pg=(rgb>>8)&0xff, pb=rgb&0xff;
            pr = (pr+1)&0xff; pg=(pg+1)&0xff; pb=(pb+1)&0xff;
            perturbed.setRGB(PERTURB_COL, PERTURB_ROW, (pr<<16)|(pg<<8)|pb);

            int[] plain2 = ImageVector.flattenRGB(perturbed);
            boolean[] Ks2 = KeySeedGenerator.generate(Ki, TIMESTAMP, plain2);
            int[] cipher2 = CipherStage.encrypt(Ks2, plain2);

            ImageIO.write(ImageVector.unflattenRGB(cipherVec, M, N), "bmp", new File(outDir + "/" + name + "_C1.bmp"));
            ImageIO.write(ImageVector.unflattenRGB(cipher2, M, N), "bmp", new File(outDir + "/" + name + "_C2.bmp"));
        }
        summaryLog.close();
        rawLog.close();
        System.out.println("Done. Results in " + outDir);
    }

    static BufferedImage copyImage(BufferedImage input) {
        int W=input.getWidth(), H=input.getHeight();
        BufferedImage copy = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<H;y++)
            for (int x=0;x<W;x++)
                copy.setRGB(x,y,input.getRGB(x,y)&0xffffff);
        return copy;
    }

    private static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    private static double std(double[] xs, double mean) {
        double s = 0;
        for (double x : xs) s += (x - mean) * (x - mean);
        return Math.sqrt(s / xs.length);
    }
}
