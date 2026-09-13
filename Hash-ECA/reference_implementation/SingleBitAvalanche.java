package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import java.util.*;

// Single-bit avalanche test: flips exactly one bit (LSB of the R channel)
// at a set of positions spanning the full raster scan order, encrypts under
// Rd = 1..4, and reports the fraction of ciphertext BITS that differ
// (standard bit-level avalanche/SAC metric, distinct from the byte-level
// NPCR/UACI already reported in Table 4/11).
public class SingleBitAvalanche {
    static final int B = 8, RS = 1;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int[] RD_VALUES = {1, 2, 3, 4};

    // 5 positions spanning the full raster scan order (start, quarter, middle,
    // three-quarter, end), directly testing the scan-position-dependent
    // propagation completeness identified in the round-count sensitivity study.
    static final int[][] POSITIONS = {
        {0, 0},       // scan start
        {128, 128},   // quarter
        {255, 255},   // middle (same position as the controlled differential test)
        {384, 384},   // three-quarter
        {511, 511},   // scan end
    };
    static final String[] POSITION_LABELS = {"start", "quarter", "middle", "three_quarter", "end"};

    public static void main(String[] args) throws Exception {
        String imagesDir = args[0];
        String keysCsv = args[1];
        String outDir = args[2];
        new File(outDir).mkdirs();

        List<String> images = new ArrayList<>();
        for (File f : new File(imagesDir).listFiles())
            if (f.getName().toLowerCase().endsWith(".bmp")) images.add(f.getPath());
        Collections.sort(images);

        List<String> keys = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(keysCsv))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                keys.add(line.split(",")[1].trim());
            }
        }

        String logPath = outDir + "/run_log.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(logPath))) {
            log.println("image,position_label,px,py,rd,keyIdx,total_bits,differing_bits,avalanche_pct,roundTripExact");
            int done = 0;
            int total = images.size() * POSITIONS.length * RD_VALUES.length * keys.size();

            for (String imgPath : images) {
                String base = new File(imgPath).getName().replaceAll("\\.(?i)bmp$", "");
                BufferedImage original = ImageIO.read(new File(imgPath));

                for (int posIdx = 0; posIdx < POSITIONS.length; posIdx++) {
                    int px = POSITIONS[posIdx][0], py = POSITIONS[posIdx][1];
                    String posLabel = POSITION_LABELS[posIdx];

                    // single-bit flip: LSB (bit 0) of the R channel only
                    BufferedImage perturbed = DiffusionV2.copyImage(original);
                    int rgb = perturbed.getRGB(px, py);
                    int R = (rgb >> 16) & 0xff, G = (rgb >> 8) & 0xff, Bc = rgb & 0xff;
                    int Rp = R ^ 0x01; // flip exactly one bit
                    perturbed.setRGB(px, py, (Rp << 16) | (G << 8) | Bc);

                    for (int rd : RD_VALUES) {
                        int keyIdx = 0;
                        for (String keyHex : keys) {
                            keyIdx++;
                            byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                            BufferedImage scrambled = ScrambleV2.scramble(original, keyBytes, B, RS);
                            BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, rd, POOL_C);

                            BufferedImage scrambledP = ScrambleV2.scramble(perturbed, keyBytes, B, RS);
                            BufferedImage encryptedP = DiffusionV2.encrypt(scrambledP, keyBytes, rd, POOL_C);

                            long totalBits = 0, diffBits = 0;
                            for (int y = 0; y < 512; y++) {
                                for (int x = 0; x < 512; x++) {
                                    int c1 = encrypted.getRGB(x, y);
                                    int c2 = encryptedP.getRGB(x, y);
                                    int xor = (c1 ^ c2) & 0xFFFFFF; // 24 RGB bits
                                    diffBits += Integer.bitCount(xor);
                                    totalBits += 24;
                                }
                            }
                            double avalanchePct = 100.0 * diffBits / totalBits;

                            boolean checked = false, exact = true;
                            if (keyIdx == 1 && posIdx == 0) {
                                BufferedImage recScr = DiffusionV2.decrypt(encrypted, keyBytes, rd, POOL_C);
                                BufferedImage recOrig = ScrambleV2.unscramble(recScr, keyBytes, B, RS);
                                exact = DiffusionV2.compareImages(original, recOrig);
                                checked = true;
                            }

                            log.println(base + "," + posLabel + "," + px + "," + py + "," + rd + "," + keyIdx + ","
                                + totalBits + "," + diffBits + "," + avalanchePct + "," + (checked ? exact : ""));
                            log.flush();
                            done++;
                            if (done % 50 == 0 || done == total)
                                System.out.println("progress " + done + "/" + total);
                        }
                    }
                }
            }
        }
        System.out.println("SINGLE-BIT AVALANCHE TEST COMPLETE.");
    }
}
