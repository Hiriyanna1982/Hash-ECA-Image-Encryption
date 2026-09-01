package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import java.util.*;

public class Section6Attacks {
    static final int B = 8, RS = 1, RD = 4;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int W = 512, H = 512;

    // fixed, arbitrary perturbation positions for the affine test (distinct from Section 5's (255,255))
    static final int AX=100, AY=100, BX=400, BY=400;
    static final int AFFINE_PAIRS_PER_KEY = 15;

    public static void main(String[] args) throws Exception {
        String imagesDir = args[0];
        String keysCsv = args[1];
        String outDir = args[2];
        new File(outDir).mkdirs();

        List<String> keys = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(keysCsv))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                keys.add(line.split(",")[1].trim());
            }
        }
        List<String> images = new ArrayList<>();
        for (File f : new File(imagesDir).listFiles())
            if (f.getName().toLowerCase().endsWith(".bmp")) images.add(f.getPath());
        Collections.sort(images);

        runAffineTest(keys, outDir);
        runEquivalentTransformTest(keys, images, outDir);
        runPermutationRecoveryTest(keys, outDir);

        System.out.println("SECTION 6 ATTACKS COMPLETE.");
    }

    static BufferedImage zeroImage() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        // default-initialized to all-zero (black) already
        return img;
    }

    static BufferedImage encryptFull(BufferedImage plain, byte[] keyBytes) throws Exception {
        BufferedImage scrambled = ScrambleV2.scramble(plain, keyBytes, B, RS);
        return DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C);
    }

    // ---------------- 6.2 Whole-cipher affine test ----------------
    // Repeated with AFFINE_PAIRS_PER_KEY randomly positioned plaintext pairs per key (not just one fixed pair),
    // so the conclusion does not rest on a single chosen plaintext relation.
    static void runAffineTest(List<String> keys, String outDir) throws Exception {
        String logPath = outDir + "/affine_test_results.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(logPath))) {
            log.println("keyIdx,pairIdx,ax,ay,bx,by,totalComparisons,exactMatches,matchRatePct,chanceRatePct");
            int keyIdx = 0;
            for (String keyHex : keys) {
                keyIdx++;
                byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                BufferedImage P0 = zeroImage();
                BufferedImage C0 = encryptFull(P0, keyBytes); // shared baseline for all pairs under this key

                Random rnd = new Random(1000L * keyIdx + 7);
                for (int pairIdx = 1; pairIdx <= AFFINE_PAIRS_PER_KEY; pairIdx++) {
                    int ax = rnd.nextInt(W), ay = rnd.nextInt(H);
                    int bx, by;
                    do { bx = rnd.nextInt(W); by = rnd.nextInt(H); } while (bx==ax && by==ay);

                    BufferedImage PA = zeroImage(); PA.setRGB(ax, ay, (1<<16));
                    BufferedImage PB = zeroImage(); PB.setRGB(bx, by, (1<<16));
                    BufferedImage PAB = zeroImage();
                    PAB.setRGB(ax, ay, (1<<16));
                    PAB.setRGB(bx, by, (1<<16));

                    BufferedImage CA = encryptFull(PA, keyBytes);
                    BufferedImage CB = encryptFull(PB, keyBytes);
                    BufferedImage CAB = encryptFull(PAB, keyBytes);

                    long total = 0, matches = 0;
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int c0 = C0.getRGB(x,y), ca = CA.getRGB(x,y), cb = CB.getRGB(x,y), cab = CAB.getRGB(x,y);
                            for (int shift = 16; shift >= 0; shift -= 8) {
                                int v0 = (c0>>shift)&0xff, va=(ca>>shift)&0xff, vb=(cb>>shift)&0xff, vab=(cab>>shift)&0xff;
                                int residual = CipherUtil.mod256(vab - va - vb + v0);
                                total++;
                                if (residual == 0) matches++;
                            }
                        }
                    }
                    double matchRate = 100.0 * matches / total;
                    double chanceRate = 100.0 / 256.0;
                    log.println(keyIdx+","+pairIdx+","+ax+","+ay+","+bx+","+by+","+total+","+matches+","+matchRate+","+chanceRate);
                    log.flush();
                }
                System.out.println("[6.2 affine] key "+keyIdx+"/"+keys.size()+": "+AFFINE_PAIRS_PER_KEY+" random pairs done");
            }
        }
    }

    // ---------------- 6.3 Equivalent-transformation chosen-plaintext test ----------------
    static void runEquivalentTransformTest(List<String> keys, List<String> images, String outDir) throws Exception {
        String logPath = outDir + "/equivalent_transform_results.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(logPath))) {
            log.println("keyIdx,image,channelMatchRatePct,pixelExactMatchRatePct,chanceChannelRatePct");
            int keyIdx = 0;
            for (String keyHex : keys) {
                keyIdx++;
                byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                BufferedImage P0 = zeroImage();
                BufferedImage C0 = encryptFull(P0, keyBytes); // "mask" M = C0 - P0 = C0 since P0=0

                for (String imgPath : images) {
                    String base = new File(imgPath).getName();
                    BufferedImage P1 = ImageIO.read(new File(imgPath));
                    BufferedImage C1 = encryptFull(P1, keyBytes);

                    long chanTotal=0, chanMatch=0, pixTotal=0, pixMatch=0;
                    for (int y=0; y<H; y++) {
                        for (int x=0; x<W; x++) {
                            int m = C0.getRGB(x,y), c1 = C1.getRGB(x,y), p1 = P1.getRGB(x,y);
                            boolean allMatch = true;
                            for (int shift=16; shift>=0; shift-=8) {
                                int mv=(m>>shift)&0xff, c1v=(c1>>shift)&0xff, p1v=(p1>>shift)&0xff;
                                int predicted = CipherUtil.mod256(c1v - mv);
                                chanTotal++;
                                if (predicted == p1v) chanMatch++; else allMatch=false;
                            }
                            pixTotal++;
                            if (allMatch) pixMatch++;
                        }
                    }
                    double chanRate = 100.0*chanMatch/chanTotal;
                    double pixRate = 100.0*pixMatch/pixTotal;
                    double chance = 100.0/256.0;
                    log.println(keyIdx+","+base+","+chanRate+","+pixRate+","+chance);
                    log.flush();
                }
                System.out.println("[6.3 equiv-transform] key "+keyIdx+"/"+keys.size()+" done");
            }
        }
    }

    // ---------------- 6.4 Permutation-recovery test ----------------
    static BufferedImage positionCodedPlaintext() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<H; y++) {
            for (int x=0; x<W; x++) {
                int idx = y*W + x; // raster index, 0..262143, fits in 18 bits (R uses only 2 bits of its 8)
                int r = (idx >> 16) & 0xff;
                int g = (idx >> 8) & 0xff;
                int bch = idx & 0xff;
                img.setRGB(x, y, (r<<16)|(g<<8)|bch);
            }
        }
        return img;
    }
    static int decodeIndex(int rgb) {
        int r=(rgb>>16)&0xff, g=(rgb>>8)&0xff, b=rgb&0xff;
        return (r<<16)|(g<<8)|b;
    }

    // ---------------- 6.4 Permutation-recovery test (zero-baseline cancellation attack) ----------------
    // Two chosen-plaintext queries per key: P0 (all-zero) and P_pos (position-coded). The attacker computes
    // D = (C_pos - C0) mod 256, attempting to cancel the reusable per-pixel diffusion contribution and recover
    // the underlying scrambling permutation directly from D, rather than from the raw ciphertext.
    static void runPermutationRecoveryTest(List<String> keys, String outDir) throws Exception {
        String logPath = outDir + "/permutation_recovery_results.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(logPath))) {
            log.println("keyIdx,totalPixels,scrambleOnlyRecoveryPct,exactMatches,exactRecoveryPct,validDecodedMappings,validRatePct,collidingPixels,unresolvedOriginalIndices");
            int keyIdx = 0;
            int totalPixels = W*H;
            for (String keyHex : keys) {
                keyIdx++;
                byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                BufferedImage PC = positionCodedPlaintext();

                // ground truth: scrambling-only output directly encodes the true original index at each position
                BufferedImage S = ScrambleV2.scramble(PC, keyBytes, B, RS);
                int[][] trueIndex = new int[W][H];
                for (int y=0;y<H;y++) for (int x=0;x<W;x++) trueIndex[x][y] = decodeIndex(S.getRGB(x,y));

                // baseline sanity check: scrambling-only self-consistency (should be 100% by construction)
                long scrambleMatches = 0;
                for (int y=0;y<H;y++) for (int x=0;x<W;x++) if (decodeIndex(S.getRGB(x,y)) == trueIndex[x][y]) scrambleMatches++;
                double scrRate = 100.0*scrambleMatches/totalPixels;

                // attack: two chosen-plaintext queries under the same key
                BufferedImage P0 = zeroImage();
                BufferedImage C0 = encryptFull(P0, keyBytes);
                BufferedImage Cpos = encryptFull(PC, keyBytes);

                int[][] decoded = new int[W][H];
                for (int y=0; y<H; y++) {
                    for (int x=0; x<W; x++) {
                        int c0 = C0.getRGB(x,y), cp = Cpos.getRGB(x,y);
                        int r = CipherUtil.mod256(((cp>>16)&0xff) - ((c0>>16)&0xff));
                        int g = CipherUtil.mod256(((cp>>8)&0xff)  - ((c0>>8)&0xff));
                        int bch = CipherUtil.mod256((cp&0xff) - (c0&0xff));
                        decoded[x][y] = (r<<16)|(g<<8)|bch; // candidate index decoded from D
                    }
                }

                // exact recovery + valid-range count
                long exactMatches = 0, validCount = 0;
                for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
                    boolean valid = decoded[x][y] <= (totalPixels - 1);
                    if (valid) validCount++;
                    if (valid && decoded[x][y] == trueIndex[x][y]) exactMatches++;
                }

                // collisions: among valid decoded values, count how many pixels share a decoded index with >=1 other pixel
                Map<Integer,Integer> countByIndex = new HashMap<>();
                for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
                    if (decoded[x][y] <= (totalPixels-1)) {
                        countByIndex.merge(decoded[x][y], 1, Integer::sum);
                    }
                }
                long collidingPixels = 0;
                Set<Integer> claimedIndices = new HashSet<>();
                for (Map.Entry<Integer,Integer> e : countByIndex.entrySet()) {
                    if (e.getValue() > 1) collidingPixels += e.getValue();
                    claimedIndices.add(e.getKey());
                }

                // unresolved: original indices 0..totalPixels-1 never produced by any decoded D value
                long unresolved = totalPixels - claimedIndices.size();

                double exactRate = 100.0*exactMatches/totalPixels;
                double validRate = 100.0*validCount/totalPixels;

                log.println(keyIdx+","+totalPixels+","+scrRate+","+exactMatches+","+exactRate+","+validCount+","+validRate+","+collidingPixels+","+unresolved);
                log.flush();
                System.out.println("[6.4 permutation] key "+keyIdx+"/"+keys.size()+": scramble-only="+String.format("%.4f",scrRate)
                    +"% cancellation-attack exact="+String.format("%.6f",exactRate)+"% valid="+String.format("%.4f",validRate)
                    +"% colliding="+collidingPixels+" unresolved="+unresolved);
            }
        }
    }
}
