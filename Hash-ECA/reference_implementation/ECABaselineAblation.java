package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import java.util.*;

// Addresses Reviewer 2 Comment 1: ECA vs no-ECA baseline comparison.
// Part A: conventional metrics (entropy/correlation/NPCR/UACI), same protocol
//         as the Table 2 ablation and R1#3 hierarchy ablation.
// Part B: the whole-cipher affine test (Section 6.2's exact protocol, 15
//         random plaintext pairs per key over 10 keys), applied to the
//         no-ECA baseline, for direct comparison against the published
//         ECA-based result (0.3899%, chance level 0.3906%).
public class ECABaselineAblation {
    static final int B = 8, RS = 1, RD = 4;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int PX = 255, PY = 255;
    static final int AFFINE_PAIRS_PER_KEY = 15;
    static final int W = 512, H = 512;

    static BufferedImage encryptFull(BufferedImage plain, byte[] keyBytes, boolean useECA) throws Exception {
        BufferedImage scrambled = ScrambleV2.scramble(plain, keyBytes, B, RS);
        return DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C, useECA);
    }
    static BufferedImage zeroImage() { return new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); }

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

        // ---- Part A: conventional metrics, ECA vs no-ECA, same 3-image x 10-key grid ----
        String statsLog = outDir + "/stats_run_log.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(statsLog))) {
            log.println("config,image,keyIdx,encPath,encPPath,roundTripExact");
            int done = 0, total = 2 * images.size() * keys.size();

            for (String cfgName : new String[]{"ECA", "NoECA"}) {
                boolean useECA = cfgName.equals("ECA");
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

                        BufferedImage scrambled = ScrambleV2.scramble(original, keyBytes, B, RS);
                        BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C, useECA);
                        BufferedImage scrambledP = ScrambleV2.scramble(perturbed, keyBytes, B, RS);
                        BufferedImage encryptedP = DiffusionV2.encrypt(scrambledP, keyBytes, RD, POOL_C, useECA);

                        boolean checked = false, exact = true;
                        if (keyIdx == 1) {
                            BufferedImage decScr = DiffusionV2.decrypt(encrypted, keyBytes, RD, POOL_C, useECA);
                            BufferedImage recOrig = ScrambleV2.unscramble(decScr, keyBytes, B, RS);
                            exact = DiffusionV2.compareImages(original, recOrig);
                            checked = true;
                        }

                        String encPath = outDir + "/" + cfgName + "_" + base + "_k" + keyIdx + "_enc.bmp";
                        String encPPath = outDir + "/" + cfgName + "_" + base + "_k" + keyIdx + "_encP.bmp";
                        ImageIO.write(encrypted, "bmp", new File(encPath));
                        ImageIO.write(encryptedP, "bmp", new File(encPPath));

                        log.println(cfgName + "," + base + "," + keyIdx + "," + encPath + "," + encPPath + "," + (checked ? exact : ""));
                        log.flush();
                        done++;
                        if (done % 20 == 0 || done == total) System.out.println("[stats] progress " + done + "/" + total);
                    }
                }
            }
        }

        // ---- Part B: whole-cipher affine test on the no-ECA baseline ----
        String affineLog = outDir + "/affine_no_eca_results.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(affineLog))) {
            log.println("keyIdx,pairIdx,ax,ay,bx,by,totalComparisons,exactMatches,matchRatePct,chanceRatePct");
            int keyIdx = 0;
            for (String keyHex : keys) {
                keyIdx++;
                byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                BufferedImage P0 = zeroImage();
                BufferedImage C0 = encryptFull(P0, keyBytes, false); // no-ECA

                Random rnd = new Random(1000L * keyIdx + 7); // same seeding convention as Section6Attacks
                for (int pairIdx = 1; pairIdx <= AFFINE_PAIRS_PER_KEY; pairIdx++) {
                    int ax = rnd.nextInt(W), ay = rnd.nextInt(H);
                    int bx, by;
                    do { bx = rnd.nextInt(W); by = rnd.nextInt(H); } while (bx == ax && by == ay);

                    BufferedImage PA = zeroImage(); PA.setRGB(ax, ay, (1 << 16));
                    BufferedImage PB = zeroImage(); PB.setRGB(bx, by, (1 << 16));
                    BufferedImage PAB = zeroImage();
                    PAB.setRGB(ax, ay, (1 << 16));
                    PAB.setRGB(bx, by, (1 << 16));

                    BufferedImage CA = encryptFull(PA, keyBytes, false);
                    BufferedImage CB = encryptFull(PB, keyBytes, false);
                    BufferedImage CAB = encryptFull(PAB, keyBytes, false);

                    long total = 0, matches = 0;
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int c0 = C0.getRGB(x, y), ca = CA.getRGB(x, y), cb = CB.getRGB(x, y), cab = CAB.getRGB(x, y);
                            for (int shift = 16; shift >= 0; shift -= 8) {
                                int v0 = (c0 >> shift) & 0xff, va = (ca >> shift) & 0xff, vb = (cb >> shift) & 0xff, vab = (cab >> shift) & 0xff;
                                int residual = CipherUtil.mod256(vab - va - vb + v0);
                                total++;
                                if (residual == 0) matches++;
                            }
                        }
                    }
                    double matchRate = 100.0 * matches / total;
                    double chanceRate = 100.0 / 256.0;
                    log.println(keyIdx + "," + pairIdx + "," + ax + "," + ay + "," + bx + "," + by + "," + total + "," + matches + "," + matchRate + "," + chanceRate);
                    log.flush();
                }
                System.out.println("[affine-no-eca] key " + keyIdx + "/" + keys.size() + " done");
            }
        }

        System.out.println("ECA BASELINE ABLATION COMPLETE.");
    }
}
