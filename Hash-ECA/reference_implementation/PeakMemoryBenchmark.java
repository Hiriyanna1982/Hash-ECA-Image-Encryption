package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;
import javax.imageio.ImageIO;

// Peak JVM heap usage benchmark, matching the methodology of Table 10:
// MemoryPoolMXBean peak-usage counters (Eden + Survivor + Tenured/Old),
// 5 discarded warm-up runs followed by 20 measured runs, for a single
// representative key.
//
// Usage: java -Xmx4g -cp out white.PeakMemoryBenchmark <imagePath> <32HexKey> <outCsv>
// The image should be 512x512 or 1024x1024 to reproduce Table 10's two rows.
public class PeakMemoryBenchmark {
    static final int B = 8, RS = 1, RD = 4;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int WARMUP = 5, MEASURED = 20;

    static List<MemoryPoolMXBean> heapPools() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getType() == java.lang.management.MemoryType.HEAP)
            .toList();
    }

    static void resetPeaks() {
        for (MemoryPoolMXBean pool : heapPools()) {
            if (pool.isUsageThresholdSupported() || true) {
                try { pool.resetPeakUsage(); } catch (Exception ignored) {}
            }
        }
    }

    static long peakUsedBytes() {
        long total = 0;
        for (MemoryPoolMXBean pool : heapPools()) {
            total += pool.getPeakUsage().getUsed();
        }
        return total;
    }

    public static void main(String[] args) throws Exception {
        String imagePath = args[0];
        String keyHex = args[1];
        String outCsv = args[2];

        byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);
        BufferedImage original = ImageIO.read(new File(imagePath));
        String imageName = new File(imagePath).getName();

        try (PrintWriter log = new PrintWriter(new FileWriter(outCsv))) {
            log.println("image,operation,run,peakHeapBytes,peakHeapMB");

            // encryption
            for (int i = 0; i < WARMUP + MEASURED; i++) {
                System.gc();
                resetPeaks();
                BufferedImage scrambled = ScrambleV2.scramble(original, keyBytes, B, RS);
                BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C);
                long peak = peakUsedBytes();
                if (i >= WARMUP) {
                    double mb = peak / 1048576.0;
                    log.println(imageName + ",encryption," + (i - WARMUP) + "," + peak + "," + mb);
                    log.flush();
                }
            }

            // decryption (encrypt once outside the timing loop to get a ciphertext to decrypt)
            BufferedImage scrambledOnce = ScrambleV2.scramble(original, keyBytes, B, RS);
            BufferedImage encryptedOnce = DiffusionV2.encrypt(scrambledOnce, keyBytes, RD, POOL_C);
            for (int i = 0; i < WARMUP + MEASURED; i++) {
                System.gc();
                resetPeaks();
                BufferedImage decScr = DiffusionV2.decrypt(encryptedOnce, keyBytes, RD, POOL_C);
                BufferedImage decrypted = ScrambleV2.unscramble(decScr, keyBytes, B, RS);
                long peak = peakUsedBytes();
                if (i >= WARMUP) {
                    double mb = peak / 1048576.0;
                    log.println(imageName + ",decryption," + (i - WARMUP) + "," + peak + "," + mb);
                    log.flush();
                }
            }
        }
        System.out.println("PEAK MEMORY BENCHMARK COMPLETE.");
    }
}
