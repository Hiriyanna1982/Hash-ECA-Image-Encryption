package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import java.util.*;

public class PerfBenchmark {
    static final int B = 8, RS = 1, RD = 4;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int WARMUP = 5;
    static final int MEASURED = 20;

    public static void main(String[] args) throws Exception {
        String imagesDir = args[0];
        String keyHex = args[1];
        String outCsv = args[2];

        List<String> images = new ArrayList<>();
        for (File f : new File(imagesDir).listFiles())
            if (f.getName().toLowerCase().endsWith(".bmp")) images.add(f.getPath());
        Collections.sort(images);

        byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

        try (PrintWriter csv = new PrintWriter(new FileWriter(outCsv))) {
            csv.println("image,run,encSec,decSec");

            for (String imagePath : images) {
                BufferedImage original = ImageIO.read(new File(imagePath)); // I/O excluded from timing below
                String base = new File(imagePath).getName();

                BufferedImage scrambledRef = ScrambleV2.scramble(original, keyBytes, B, RS);
                BufferedImage encryptedRef = DiffusionV2.encrypt(scrambledRef, keyBytes, RD, POOL_C);

                int totalRuns = WARMUP + MEASURED;
                for (int i = 0; i < totalRuns; i++) {
                    long t0 = System.nanoTime();
                    BufferedImage scrambled = ScrambleV2.scramble(original, keyBytes, B, RS);
                    BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C);
                    long t1 = System.nanoTime();
                    double encSec = (t1 - t0) / 1e9;

                    long t2 = System.nanoTime();
                    BufferedImage decScr = DiffusionV2.decrypt(encryptedRef, keyBytes, RD, POOL_C);
                    BufferedImage decrypted = ScrambleV2.unscramble(decScr, keyBytes, B, RS);
                    long t3 = System.nanoTime();
                    double decSec = (t3 - t2) / 1e9;

                    if (i >= WARMUP) {
                        csv.println(base + "," + (i-WARMUP) + "," + encSec + "," + decSec);
                        csv.flush();
                    }
                }
                System.out.println("done: " + base);
            }
        }
        System.out.println("BENCHMARK COMPLETE.");
    }
}
