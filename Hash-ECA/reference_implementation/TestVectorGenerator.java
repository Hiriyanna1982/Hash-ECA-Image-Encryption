package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import javax.imageio.ImageIO;
import java.util.*;

public class TestVectorGenerator {
    static final int W = 16, H = 16, B = 8, RS = 1, RD = 4;
    static final int[] POOL_C = {30, 45, 106, 184};
    static final String KEY_HEX = "3F9A7C2D8E4B1A6F9D0C3E7B5A2F8D1D"; // existing documented default key

    static PrintWriter out;

    public static void main(String[] args) throws Exception {
        String outPath = args.length > 0 ? args[0] : "test_vector_output.txt";
        out = new PrintWriter(new FileWriter(outPath));

        byte[] keyBytes = CipherUtil.keyBytesFromHex(KEY_HEX);
        log("=== TEST VECTOR: 16x16 RGB, B=8, Rs=1, Rd=4, Rpool={30,45,106,184} ===");
        log("Key (hex): " + KEY_HEX);
        log("Key bytes (Kb, hex): " + hex(keyBytes));
        log("");

        // ---- Plaintext: P[i] = i mod 256, flattened row-major R,G,B, i=0..767 ----
        BufferedImage plain = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int idx = 0;
        int[] plainBytes = new int[W*H*3];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int r = idx % 256; idx++;
                int g = idx % 256; idx++;
                int b = idx % 256; idx++;
                plainBytes[y*W*3 + x*3 + 0] = r;
                plainBytes[y*W*3 + x*3 + 1] = g;
                plainBytes[y*W*3 + x*3 + 2] = b;
                plain.setRGB(x, y, (r<<16)|(g<<8)|b);
            }
        }
        log("Plaintext specification: flattened row-major byte i (i=0..767, R,G,B interleaved per pixel) = i mod 256.");
        log("Plaintext bytes (768, hex, row-major R,G,B):");
        log(hexList(plainBytes));
        log("");

        // ---- Scrambling trace ----
        log("--- SCRAMBLING (round r=1) ---");
        byte[] K0 = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_SCRAMBLE_INIT});
        log("K0 = SHA256(Kb || TAG_init) = " + hex(K0));

        byte[] Hblock = CipherUtil.sha256(K0, CipherUtil.be32(1), new byte[]{CipherUtil.TAG_SCRAMBLE_BLOCK});
        log("H_1,block = SHA256(K0 || BE32(1) || TAG_block) = " + hex(Hblock));
        int[] blockPerm = tracedPermutation((W/B)*(H/B), Hblock);
        log("Block permutation (4 blocks, 8x8 each): " + Arrays.toString(blockPerm));

        byte[] Hcolumn = CipherUtil.sha256(K0, CipherUtil.be32(1), new byte[]{CipherUtil.TAG_SCRAMBLE_COLUMN});
        log("H_1,column = SHA256(K0 || BE32(1) || TAG_column) = " + hex(Hcolumn));
        int[] colPerm = tracedPermutation(W, Hcolumn);
        log("Column permutation (16 columns): " + Arrays.toString(colPerm));

        byte[] Hrow = CipherUtil.sha256(K0, CipherUtil.be32(1), new byte[]{CipherUtil.TAG_SCRAMBLE_ROW});
        log("H_1,row = SHA256(K0 || BE32(1) || TAG_row) = " + hex(Hrow));
        int[] rowPerm = tracedPermutation(H, Hrow);
        log("Row permutation (16 rows): " + Arrays.toString(rowPerm));

        byte[] Hpixel = CipherUtil.sha256(K0, CipherUtil.be32(1), new byte[]{CipherUtil.TAG_SCRAMBLE_PIXEL});
        log("H_1,pixel = SHA256(K0 || BE32(1) || TAG_pixel) = " + hex(Hpixel));
        int[] pixelPerm = tracedPermutation(W*H, Hpixel);
        log("Pixel permutation (256 pixels): " + Arrays.toString(pixelPerm));
        log("");

        BufferedImage scrambled = ScrambleV2.scramble(plain, keyBytes, B, RS);
        int[] scrambledBytes = imageToBytes(scrambled);
        log("Scrambled image bytes (768, hex, row-major R,G,B):");
        log(hexList(scrambledBytes));
        log("");

        // ---- Diffusion trace ----
        log("--- DIFFUSION (round r=1, forward scan) ---");
        byte[] Sm = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_DIFFUSION_INIT});
        log("Sm = SHA256(Kb || TAG_diff-init) = " + hex(Sm));
        byte[] S = CipherUtil.sha256(Sm, new byte[]{CipherUtil.TAG_DIFFUSION_ROUND}, CipherUtil.be32(1));
        log("Round-start state S (r=1) = SHA256(Sm || TAG_diff-round || BE32(1)) = " + hex(S));
        int F = S[2] & 0xff;
        log("Initial feedback F = Byte3(S) = " + F);
        log("");

        int tracedPixels = 3;
        int count = 0;
        for (int y = 0; y < H && count < tracedPixels; y++) {
            for (int x = 0; x < W && count < tracedPixels; x++, count++) {
                int rgb = scrambled.getRGB(x,y);
                int PR=(rgb>>16)&0xff, PG=(rgb>>8)&0xff, PB=rgb&0xff;
                int Pmix = ((x*x + 3*x*y + y*y) ^ (x+y)) & 0xff;
                int EseedR = Pmix ^ (S[0]&0xff), EseedG = Pmix ^ (S[1]&0xff), EseedB = Pmix ^ (S[2]&0xff);
                int ruleR = POOL_C[(S[0]&0xff) % POOL_C.length];
                int ruleG = POOL_C[(S[1]&0xff) % POOL_C.length];
                int ruleB = POOL_C[(S[2]&0xff) % POOL_C.length];
                int maskR = DiffusionV2.ecaOneStep(EseedR, ruleR);
                int maskG = DiffusionV2.ecaOneStep(EseedG, ruleG);
                int maskB = DiffusionV2.ecaOneStep(EseedB, ruleB);
                int CR = CipherUtil.mod256(PR + maskR + F);
                int CG = CipherUtil.mod256(PG + maskG + CR);
                int CB = CipherUtil.mod256(PB + maskB + CG);

                log("Pixel (x=" + x + ", y=" + y + "):");
                log("  P_R,G,B (scrambled-domain plaintext) = " + PR + ", " + PG + ", " + PB);
                log("  P_mix = " + Pmix);
                log("  Eseed_R,G,B = " + EseedR + ", " + EseedG + ", " + EseedB);
                log("  rule_R,G,B = " + ruleR + ", " + ruleG + ", " + ruleB + "  (selected via Byte(S) mod |Rpool|)");
                log("  Mask_R,G,B = " + maskR + ", " + maskG + ", " + maskB);
                log("  F (feedback in) = " + F);
                log("  C_R,G,B = " + CR + ", " + CG + ", " + CB);

                byte[] Snext = CipherUtil.sha256(S, CipherUtil.be32(x), CipherUtil.be32(y), CipherUtil.be32(1),
                    new byte[]{CipherUtil.TAG_DIFFUSION_PIXEL}, new byte[]{(byte)PR,(byte)PG,(byte)PB,(byte)CR,(byte)CG,(byte)CB});
                log("  S_next = SHA256(S || BE32(x) || BE32(y) || BE32(1) || TAG_diff-pixel || PR||PG||PB||CR||CG||CB) = " + hex(Snext));
                log("");
                S = Snext;
                F = CB;
            }
        }
        log("(Remaining 253 pixels of round 1, and rounds 2-4, follow the identical state-chaining procedure and are reproducible by re-running this generator; only the first three pixels are shown here for brevity.)");
        log("");

        BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, RD, POOL_C);
        int[] cipherBytes = imageToBytes(encrypted);
        log("--- FINAL CIPHERTEXT (after Rd=4 diffusion rounds) ---");
        log("Ciphertext bytes (768, hex, row-major R,G,B):");
        log(hexList(cipherBytes));
        log("");

        // ---- Round-trip verification ----
        BufferedImage decScr = DiffusionV2.decrypt(encrypted, keyBytes, RD, POOL_C);
        BufferedImage recovered = ScrambleV2.unscramble(decScr, keyBytes, B, RS);
        boolean exact = DiffusionV2.compareImages(plain, recovered);
        log("--- ROUND-TRIP VERIFICATION ---");
        log("Decrypt(Encrypt(P, K), K) == P (exact byte match): " + exact);

        out.close();
        System.out.println("Test vector written to " + outPath + ". Round-trip exact: " + exact);
    }

    static int[] tracedPermutation(int n, byte[] seed) throws Exception {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        for (int i = n-1; i > 0; i--) {
            byte[] d = CipherUtil.sha256(seed, CipherUtil.be32(i));
            int j = CipherUtil.positiveIntFromFirst4Bytes(d) % (i+1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        return perm;
    }

    static int[] imageToBytes(BufferedImage img) {
        int[] out = new int[W*H*3];
        int k=0;
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
            int rgb = img.getRGB(x,y);
            out[k++] = (rgb>>16)&0xff; out[k++]=(rgb>>8)&0xff; out[k++]=rgb&0xff;
        }
        return out;
    }

    static void log(String s) { out.println(s); }
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
    static String hexList(int[] vals) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<vals.length;i++) {
            sb.append(String.format("%02X", vals[i]));
            if (i < vals.length-1) sb.append(i%24==23 ? "\n" : " ");
        }
        return sb.toString();
    }
}
