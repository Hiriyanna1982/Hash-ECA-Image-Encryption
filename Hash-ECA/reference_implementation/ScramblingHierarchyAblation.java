package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import java.util.*;

// Addresses Reviewer 1 Comment 3: quantifies the incremental benefit of the
// block/column/row scrambling stages beyond the final global pixel shuffle,
// and reports their separate computational cost.
//
// "Pixel-only" reuses ScrambleV2's exact key-derivation chain (same K0 init,
// same TAG_SCRAMBLE_PIXEL digest, same per-round TAG_SCRAMBLE_UPDATE chaining)
// so the comparison isolates exactly the block/column/row stages' contribution,
// with no other confound.
public class ScramblingHierarchyAblation {
    static final int B = 8, RS = 1, RD = 4;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int PX = 255, PY = 255;
    static final int SCRAMBLE_TIMING_WARMUP = 5, SCRAMBLE_TIMING_MEASURED = 20;

    static BufferedImage scramblePixelOnly(BufferedImage img, byte[] keyBytes, int R) throws Exception {
        BufferedImage current = ScrambleV2.copyImage(img);
        byte[] K = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_SCRAMBLE_INIT});
        for (int round = 1; round <= R; round++) {
            byte[] Hp = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_PIXEL});
            current = ScrambleV2.pixelShuffle(current, Hp);
            K = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_UPDATE});
        }
        return current;
    }

    static BufferedImage unscramblePixelOnly(BufferedImage img, byte[] keyBytes, int R) throws Exception {
        byte[][] Hp = new byte[R + 1][];
        byte[] K = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_SCRAMBLE_INIT});
        for (int round = 1; round <= R; round++) {
            Hp[round] = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_PIXEL});
            K = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_UPDATE});
        }
        BufferedImage current = ScrambleV2.copyImage(img);
        for (int round = R; round >= 1; round--) {
            current = ScrambleV2.pixelShuffleInverse(current, Hp[round]);
        }
        return current;
    }

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

        // ---- Part A: statistical comparison (full hierarchy vs pixel-only), full pipeline ----
        String statsLog = outDir + "/stats_run_log.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(statsLog))) {
            log.println("config,image,keyIdx,encPath,encPPath,roundTripExact");
            int done = 0, total = 2 * images.size() * keys.size();

            for (String cfg : new String[]{"FullHierarchy", "PixelOnly"}) {
                for (String imgPath : images) {
                    String base = new File(imgPath).getName().replaceAll("\\.(?i)bmp$", "");
                    BufferedImage original = ImageIO.read(new File(imgPath));

                    BufferedImage perturbed = DiffusionV2.copyImage(original);
                    int rgb = perturbed.getRGB(PX, PY);
                    int R = (rgb >> 16) & 0xff, G = (rgb >> 8) & 0xff, Bc = rgb & 0xff;
                    perturbed.setRGB(PX, PY, (((R + 1) % 256) << 16) | (((G + 1) % 256) << 8) | ((Bc + 1) % 256));

                    int keyIdx = 0;
                    for (String keyHex : keys) {
                        keyIdx++;
                        byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                        BufferedImage scrambled, scrambledP;
                        if (cfg.equals("FullHierarchy")) {
                            scrambled = ScrambleV2.scramble(original, keyBytes, B, RS);
                            scrambledP = ScrambleV2.scramble(perturbed, keyBytes, B, RS);
                        } else {
                            scrambled = scramblePixelOnly(original, keyBytes, RS);
                            scrambledP = scramblePixelOnly(perturbed, keyBytes, RS);
                        }
                        BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C);
                        BufferedImage encryptedP = DiffusionV2.encrypt(scrambledP, keyBytes, RD, POOL_C);

                        boolean checked = false, exact = true;
                        if (keyIdx == 1) {
                            BufferedImage recScr = DiffusionV2.decrypt(encrypted, keyBytes, RD, POOL_C);
                            BufferedImage recOrig;
                            if (cfg.equals("FullHierarchy")) {
                                recOrig = ScrambleV2.unscramble(recScr, keyBytes, B, RS);
                            } else {
                                recOrig = unscramblePixelOnly(recScr, keyBytes, RS);
                            }
                            exact = DiffusionV2.compareImages(original, recOrig);
                            checked = true;
                        }

                        String encPath = outDir + "/" + cfg + "_" + base + "_k" + keyIdx + "_enc.bmp";
                        String encPPath = outDir + "/" + cfg + "_" + base + "_k" + keyIdx + "_encP.bmp";
                        ImageIO.write(encrypted, "bmp", new File(encPath));
                        ImageIO.write(encryptedP, "bmp", new File(encPPath));

                        log.println(cfg + "," + base + "," + keyIdx + "," + encPath + "," + encPPath + "," + (checked ? exact : ""));
                        log.flush();
                        done++;
                        if (done % 20 == 0 || done == total) System.out.println("[stats] progress " + done + "/" + total);
                    }
                }
            }
        }

        // ---- Part B: separate scrambling-stage-only timing cost (full hierarchy vs pixel-only) ----
        String timingLog = outDir + "/timing_run_log.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(timingLog))) {
            log.println("config,image,run,scrambleSec");
            byte[] keyBytes = CipherUtil.keyBytesFromHex(keys.get(0)); // fixed representative key, matching Table 9's convention

            for (String imgPath : images) {
                BufferedImage original = ImageIO.read(new File(imgPath));
                String base = new File(imgPath).getName();

                for (String cfg : new String[]{"FullHierarchy", "PixelOnly"}) {
                    int totalRuns = SCRAMBLE_TIMING_WARMUP + SCRAMBLE_TIMING_MEASURED;
                    for (int i = 0; i < totalRuns; i++) {
                        long t0 = System.nanoTime();
                        if (cfg.equals("FullHierarchy")) {
                            ScrambleV2.scramble(original, keyBytes, B, RS);
                        } else {
                            scramblePixelOnly(original, keyBytes, RS);
                        }
                        long t1 = System.nanoTime();
                        double sec = (t1 - t0) / 1e9;
                        if (i >= SCRAMBLE_TIMING_WARMUP) {
                            log.println(cfg + "," + base + "," + (i - SCRAMBLE_TIMING_WARMUP) + "," + sec);
                            log.flush();
                        }
                    }
                }
                System.out.println("[timing] done: " + base);
            }
        }

        System.out.println("SCRAMBLING HIERARCHY ABLATION COMPLETE.");
    }
}
