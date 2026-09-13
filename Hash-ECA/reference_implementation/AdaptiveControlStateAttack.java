package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

// "Adaptive chosen-plaintext control-state inference and mask-prediction attack"
//
// Part A (per key): from a single all-zero chosen-plaintext query against the
// reduced Rd=1 cipher, exactly recover the per-pixel diffusion masks, then for
// each recovered mask enumerate every candidate 8-bit state byte consistent
// with it (brute force over the public Pmix/rule-selection/ECA pipeline).
//
// Part B (per key, per target): use pixel 1's recovered mask+feedback offset
// (which is plaintext-independent, since pixel 1 has no preceding ciphertext
// feedback within the round) to adaptively choose a second uniform plaintext
// that forces the first ciphertext pixel to a chosen target value, then test
// whether that success propagates to pixel 2 onward once the SHA-256 state
// update has mixed in the (possibly different) plaintext/ciphertext pair.
public class AdaptiveControlStateAttack {
    static final int B = 8, RS = 1, RD = 1; // reduced cipher: Rd=1
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int W = 512, H = 512;

    static final int[][] TARGETS = {
        {0, 0, 0}, {255, 255, 255}, {128, 64, 192}, {17, 201, 88}
    };

    // ---- shadow instrumentation: recompute the TRUE internal masks/state bytes
    // independently, for scoring the attack only -- never given to the attack logic. ----
    static int[][] shadowTrueMaskR, shadowTrueMaskG, shadowTrueMaskB;
    static int[][] shadowTrueStateByte0, shadowTrueStateByte1, shadowTrueStateByte2; // Byte1/2/3(S) per pixel

    static BufferedImage uniformImage(int r, int g, int b) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int rgb = (r << 16) | (g << 8) | b;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                img.setRGB(x, y, rgb);
        return img;
    }

    // Runs the reduced Rd=1 diffusion stage on a uniform plaintext, recording the
    // TRUE internal masks and state bytes at every pixel (shadow instrumentation)
    // alongside the normal ciphertext output.
    static BufferedImage encryptShadowed(BufferedImage scrambled, byte[] keyBytes, int plainVal) throws Exception {
        byte[] Sm = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_DIFFUSION_INIT});
        byte[] S = CipherUtil.sha256(Sm, new byte[]{CipherUtil.TAG_DIFFUSION_ROUND}, CipherUtil.be32(1));
        int F = S[2] & 0xff;

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int PR = (plainVal >> 16) & 0xff, PG = (plainVal >> 8) & 0xff, PB = plainVal & 0xff;

        shadowTrueMaskR = new int[W][H]; shadowTrueMaskG = new int[W][H]; shadowTrueMaskB = new int[W][H];
        shadowTrueStateByte0 = new int[W][H]; shadowTrueStateByte1 = new int[W][H]; shadowTrueStateByte2 = new int[W][H];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                shadowTrueStateByte0[x][y] = S[0] & 0xff;
                shadowTrueStateByte1[x][y] = S[1] & 0xff;
                shadowTrueStateByte2[x][y] = S[2] & 0xff;

                int[] m = DiffusionV2.masksFromState(S, x, y, POOL_C, true);
                shadowTrueMaskR[x][y] = m[0]; shadowTrueMaskG[x][y] = m[1]; shadowTrueMaskB[x][y] = m[2];

                int CR = CipherUtil.mod256(PR + m[0] + F);
                int CG = CipherUtil.mod256(PG + m[1] + CR);
                int CB = CipherUtil.mod256(PB + m[2] + CG);
                out.setRGB(x, y, (CR << 16) | (CG << 8) | CB);

                S = DiffusionV2.nextState(S, x, y, 1, PR, PG, PB, CR, CG, CB);
                F = CB;
            }
        }
        return out;
    }

    // Attacker-side EXACT mask recovery from a known-plaintext/observed-ciphertext
    // pair (forward scan order, i>1 uses the previous pixel's observed C_B as
    // feedback; pixel 1 is handled separately since it has no predecessor).
    static int[][][] recoverMasks(BufferedImage cipher, int plainVal) {
        int PR = (plainVal >> 16) & 0xff, PG = (plainVal >> 8) & 0xff, PB = plainVal & 0xff;
        int[][] MR = new int[W][H], MG = new int[W][H], MB = new int[W][H];
        int prevCB = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = cipher.getRGB(x, y);
                int CR = (rgb >> 16) & 0xff, CG = (rgb >> 8) & 0xff, CB = rgb & 0xff;
                if (prevCB >= 0) { // i>1
                    MR[x][y] = CipherUtil.mod256(CR - PR - prevCB);
                    MG[x][y] = CipherUtil.mod256(CG - PG - CR);
                    MB[x][y] = CipherUtil.mod256(CB - PB - CG);
                } else { // pixel 1: no predecessor; mask+feedback recovered jointly (see main())
                    MR[x][y] = -1; MG[x][y] = -1; MB[x][y] = -1;
                }
                prevCB = CB;
            }
        }
        return new int[][][]{MR, MG, MB};
    }

    // Candidate state-byte enumeration for one recovered mask value on one channel:
    // brute force over all 256 possible state bytes b, using ONLY public information
    // (Pmix at that position, the fixed rule pool) plus the observed mask.
    static List<Integer> enumerateCandidates(int x, int y, int observedMask) {
        int posMix = ((x*x + 3*x*y + y*y) ^ (x+y)) & 0xff;
        List<Integer> survivors = new ArrayList<>();
        for (int b = 0; b < 256; b++) {
            int eseed = posMix ^ b;
            int rule = POOL_C[b % POOL_C.length];
            int mask = DiffusionV2.ecaOneStep(eseed, rule);
            if (mask == observedMask) survivors.add(b);
        }
        return survivors;
    }

    public static void main(String[] args) throws Exception {
        String keysCsv = args[0];
        String outDir = args[1];
        new File(outDir).mkdirs();

        List<String> keys = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(keysCsv))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                keys.add(line.split(",")[1].trim());
            }
        }

        // ---- Part A: candidate-byte enumeration from a single all-zero query ----
        String partA = outDir + "/partA_candidate_enumeration.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(partA))) {
            log.println("keyIdx,channel,pixelIndex,x,y,observedMask,trueByte,numCandidates,trueByteIncluded,unique");
            int keyIdx = 0;
            for (String keyHex : keys) {
                keyIdx++;
                byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);
                BufferedImage plain = uniformImage(0, 0, 0);
                BufferedImage scrambled = ScrambleV2.scramble(plain, keyBytes, B, RS); // no-op on values (all-zero)
                BufferedImage cipher = encryptShadowed(scrambled, keyBytes, 0);

                int[][][] recovered = recoverMasks(cipher, 0);
                int[][] MR = recovered[0], MG = recovered[1], MB = recovered[2];

                // sample pixels 2..N (skip pixel 1, handled without recovery here) -- use every 50th
                // pixel in scan order for a large, representative, computationally tractable sample.
                int pixelIndex = 0;
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        pixelIndex++;
                        if (pixelIndex == 1) continue; // pixel 1 has no recovered mask here
                        if (pixelIndex % 50 != 0) continue;

                        int[] trueBytes = {shadowTrueStateByte0[x][y], shadowTrueStateByte1[x][y], shadowTrueStateByte2[x][y]};
                        int[][][] MM = {MR, MG, MB};
                        String[] chLabel = {"R", "G", "B"};
                        for (int c = 0; c < 3; c++) {
                            int observed = MM[c][x][y];
                            List<Integer> cands = enumerateCandidates(x, y, observed);
                            boolean trueIncluded = cands.contains(trueBytes[c]);
                            boolean unique = cands.size() == 1;
                            log.println(keyIdx + "," + chLabel[c] + "," + pixelIndex + "," + x + "," + y + "," +
                                observed + "," + trueBytes[c] + "," + cands.size() + "," + trueIncluded + "," + unique);
                        }
                    }
                }
                log.flush();
                System.out.println("[PartA] key " + keyIdx + "/" + keys.size() + " done");
            }
        }

        // ---- Part B: adaptive two-query prediction attack ----
        String partB = outDir + "/partB_adaptive_prediction.csv";
        try (PrintWriter log = new PrintWriter(new FileWriter(partB))) {
            log.println("keyIdx,targetIdx,targetR,targetG,targetB,firstPixelSuccess," +
                "nextPixelMaskPredR,nextPixelMaskPredG,nextPixelMaskPredB," +
                "cipherBytePredRate,fullPixelPredRate,firstDivergencePixelIndex,totalPixels");

            int keyIdx = 0;
            for (String keyHex : keys) {
                keyIdx++;
                byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

                // Query 1: all-zero uniform plaintext
                BufferedImage plain1 = uniformImage(0, 0, 0);
                BufferedImage scrambled1 = ScrambleV2.scramble(plain1, keyBytes, B, RS);
                BufferedImage C1 = encryptShadowed(scrambled1, keyBytes, 0);
                int[][][] recovered1 = recoverMasks(C1, 0);

                // pixel 1's per-channel masks, learned exactly from Query 1 (P1=0 everywhere):
                // C_R,1 = Mask_R,1 + F1  =>  Mask_R,1+F1 recovered directly as C1(pixel1).R
                // C_G,1 = Mask_G,1 + C_R,1  =>  Mask_G,1 = C1(pixel1).G - C1(pixel1).R
                // C_B,1 = Mask_B,1 + C_G,1  =>  Mask_B,1 = C1(pixel1).B - C1(pixel1).G
                // (cross-channel feedback means only the R-channel prediction is a simple fixed
                // offset; G and B depend on the *changed* C_R/C_G under the new chosen plaintext.)
                int c1PixelOneRgb = C1.getRGB(0, 0);
                int offsetR = (c1PixelOneRgb >> 16) & 0xff, offsetG = (c1PixelOneRgb >> 8) & 0xff, offsetB = c1PixelOneRgb & 0xff;
                int maskG1 = CipherUtil.mod256(offsetG - offsetR);
                int maskB1 = CipherUtil.mod256(offsetB - offsetG);

                for (int t = 0; t < TARGETS.length; t++) {
                    int tr = TARGETS[t][0], tg = TARGETS[t][1], tb = TARGETS[t][2];

                    // adaptively chosen uniform plaintext for Query 2, using ONLY information
                    // learned from Query 1 (the pixel-1 masks), correctly accounting for
                    // cross-channel feedback cascading through the target values themselves.
                    int pR = CipherUtil.mod256(tr - offsetR);
                    int pG = CipherUtil.mod256(tg - maskG1 - tr);
                    int pB = CipherUtil.mod256(tb - maskB1 - tg);
                    int plainVal2 = (pR << 16) | (pG << 8) | pB;

                    BufferedImage plain2 = uniformImage(pR, pG, pB);
                    BufferedImage scrambled2 = ScrambleV2.scramble(plain2, keyBytes, B, RS);
                    BufferedImage C2 = encryptShadowed(scrambled2, keyBytes, plainVal2);
                    int[][][] recovered2 = recoverMasks(C2, plainVal2);

                    int c2PixelOneRgb = C2.getRGB(0, 0);
                    int actualR = (c2PixelOneRgb >> 16) & 0xff, actualG = (c2PixelOneRgb >> 8) & 0xff, actualB = c2PixelOneRgb & 0xff;
                    boolean firstPixelSuccess = (actualR == tr && actualG == tg && actualB == tb);

                    // naive adaptive prediction strategy: assume Query 2's masks equal Query 1's
                    // recovered masks at the same pixel position (the only information available
                    // from a single prior query). Predicted ciphertext bytes are computed by
                    // cascading this naive mask assumption FORWARD through the same add-chain the
                    // cipher itself uses, starting from the certain pixel-1 prediction (=target) and
                    // using only PREDICTED (never actual) values as the running feedback -- this
                    // avoids any circularity in measuring genuine forward prediction accuracy.
                    int[][] MR1 = recovered1[0], MG1 = recovered1[1], MB1 = recovered1[2];
                    int[][] MR2 = recovered2[0], MG2 = recovered2[1], MB2 = recovered2[2];

                    long totalPixels = 0, maskMatchR = 0, maskMatchG = 0, maskMatchB = 0;
                    long cipherByteTotal = 0, cipherByteMatches = 0, fullPixelMatches = 0;
                    int firstDivergence = -1;

                    int predF = tb; // predicted feedback entering pixel 2 = target's B component (certain)
                    int pixelIndex = 0;
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            pixelIndex++;
                            if (pixelIndex == 1) continue; // pixel 1 excluded (adaptively controlled, not predicted)
                            totalPixels++;

                            boolean mR = MR1[x][y] == MR2[x][y];
                            boolean mG = MG1[x][y] == MG2[x][y];
                            boolean mB = MB1[x][y] == MB2[x][y];
                            if (mR) maskMatchR++;
                            if (mG) maskMatchG++;
                            if (mB) maskMatchB++;
                            if (mR && mG && mB) fullPixelMatches++;

                            // forward-only naive ciphertext-byte prediction using ONLY predicted values
                            int predCR = CipherUtil.mod256(pR + MR1[x][y] + predF);
                            int predCG = CipherUtil.mod256(pG + MG1[x][y] + predCR);
                            int predCB = CipherUtil.mod256(pB + MB1[x][y] + predCG);

                            int rgb2 = C2.getRGB(x, y);
                            int actC2R = (rgb2 >> 16) & 0xff, actC2G = (rgb2 >> 8) & 0xff, actC2B = rgb2 & 0xff;

                            cipherByteTotal += 3;
                            if (predCR == actC2R) cipherByteMatches++;
                            if (predCG == actC2G) cipherByteMatches++;
                            if (predCB == actC2B) cipherByteMatches++;

                            if (firstDivergence == -1 && !(mR && mG && mB)) {
                                firstDivergence = pixelIndex;
                            }
                            predF = predCB; // cascade forward using the PREDICTED value only
                        }
                    }

                    double maskPredR = 100.0 * maskMatchR / totalPixels;
                    double maskPredG = 100.0 * maskMatchG / totalPixels;
                    double maskPredB = 100.0 * maskMatchB / totalPixels;
                    double cipherBytePredRate = 100.0 * cipherByteMatches / cipherByteTotal;
                    double fullPixelRate = 100.0 * fullPixelMatches / totalPixels;

                    log.println(keyIdx + "," + t + "," + tr + "," + tg + "," + tb + "," + firstPixelSuccess + "," +
                        maskPredR + "," + maskPredG + "," + maskPredB + "," +
                        cipherBytePredRate + "," + fullPixelRate + "," + firstDivergence + "," + totalPixels);
                    log.flush();
                }
                System.out.println("[PartB] key " + keyIdx + "/" + keys.size() + " done");
            }
        }

        System.out.println("ADAPTIVE CONTROL-STATE ATTACK COMPLETE.");
    }
}
