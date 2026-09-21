package lvrca;

import java.io.File;
import java.io.PrintWriter;

public final class Main {

    // Reference/published 256-bit key, used verbatim for all three channels
    // from the authors' reference main.cpp.
    static final String KEY = "EFC796D47FDFFF69AB7DF3DFFF3CE7AFDEFEFC6977757FC9DA69D93F4D76FC7F";

    static final int PERTURB_ROW = 255, PERTURB_COL = 255;
    static final int WARMUP_RUNS = 5;
    static final int MEASURED_RUNS = 20;

    public static void main(String[] args) throws Exception {
        String inDir = args.length > 0 ? args[0] : "images_locked";
        String outDir = args.length > 1 ? args[1] : "results";
        new File(outDir).mkdirs();

        String[] names = {"Airplane","Baboon","Barbara","Boats","House","Lena","Monarch","Pepper","Sailboat","Tiffany"};

        PrintWriter summaryLog = new PrintWriter(new File(outDir, "timing_ms.csv"));
        summaryLog.println("image,encrypt_ms_mean,encrypt_ms_std,decrypt_ms_mean,decrypt_ms_std,roundtrip_ok,mismatched_bytes,warmup_runs,measured_runs");

        PrintWriter rawLog = new PrintWriter(new File(outDir, "timing_runs_ms.csv"));
        rawLog.println("image,run_index,encrypt_ms,decrypt_ms");

        for (String name : names) {
            String path = inDir + "/" + name + ".bmp";
            BmpIO.Image img = BmpIO.read(path);
            int m = img.m, n = img.n;
            if (m % 8 != 0 || n % 8 != 0) {
                throw new IllegalStateException(name + " dimensions not divisible by 8: " + m + "x" + n);
            }

            // ---- warm-up ----
            for (int w = 0; w < WARMUP_RUNS; w++) {
                byte[][] enc = RgbCipher.encryptColor(img.planes, m, n, KEY);
                RgbCipher.decryptColor(enc, m, n, KEY);
            }

            // ---- measured runs ----
            double[] encMsRuns = new double[MEASURED_RUNS];
            double[] decMsRuns = new double[MEASURED_RUNS];
            boolean roundtripOk = true;
            long mismatches = 0;
            byte[][] cipherPlanes = null;

            for (int r = 0; r < MEASURED_RUNS; r++) {
                // Encryption time covers the complete three-channel processing.
                long t0 = System.nanoTime();
                byte[][] enc = RgbCipher.encryptColor(img.planes, m, n, KEY);
                long t1 = System.nanoTime();
                byte[][] dec = RgbCipher.decryptColor(enc, m, n, KEY);
                long t2 = System.nanoTime();

                encMsRuns[r] = (t1 - t0) / 1e6;
                decMsRuns[r] = (t2 - t1) / 1e6;
                rawLog.printf("%s,%d,%.3f,%.3f%n", name, r, encMsRuns[r], decMsRuns[r]);

                for (int c = 0; c < 3; c++) {
                    for (int i = 0; i < m * n; i++) {
                        if (img.planes[c][i] != dec[c][i]) { roundtripOk = false; mismatches++; }
                    }
                }
                cipherPlanes = enc;
            }

            double encMean = mean(encMsRuns), encStd = std(encMsRuns, encMean);
            double decMean = mean(decMsRuns), decStd = std(decMsRuns, decMean);

            System.out.printf("%-10s encrypt=%.2f+/-%.2fms decrypt=%.2f+/-%.2fms roundtrip=%s mismatches=%d (n=%d warmup, %d measured)%n",
                    name, encMean, encStd, decMean, decStd, roundtripOk ? "OK" : "FAIL", mismatches, WARMUP_RUNS, MEASURED_RUNS);
            summaryLog.printf("%s,%.3f,%.3f,%.3f,%.3f,%s,%d,%d,%d%n",
                    name, encMean, encStd, decMean, decStd, roundtripOk, mismatches, WARMUP_RUNS, MEASURED_RUNS);

            BmpIO.write(outDir + "/" + name + "_cipher.bmp", m, n, cipherPlanes);

            // ---- differential attack pair ----
            byte[][] perturbed = new byte[][]{
                    img.planes[0].clone(), img.planes[1].clone(), img.planes[2].clone()
            };
            int idx = PERTURB_ROW * n + PERTURB_COL;
            for (int c = 0; c < 3; c++) {
                perturbed[c][idx] = (byte) (((perturbed[c][idx] & 0xFF) + 1) & 0xFF);
            }
            byte[][] c1 = cipherPlanes;
            byte[][] c2 = RgbCipher.encryptColor(perturbed, m, n, KEY);
            BmpIO.write(outDir + "/" + name + "_C1.bmp", m, n, c1);
            BmpIO.write(outDir + "/" + name + "_C2.bmp", m, n, c2);
        }
        summaryLog.close();
        rawLog.close();
        System.out.println("Done. Results in " + outDir);
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
