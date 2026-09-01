package white;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class ScrambleV2 {

    static final int DEFAULT_BLOCK_SIZE = 8;
    static final int DEFAULT_ROUNDS = 1;
    static final String DEFAULT_KEY_HEX = "3F9A7C2D8E4B1A6F9D0C3E7B5A2F8D1D";

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 5) {
            System.out.println("Usage: java white.ScrambleV2 <input> [output] [32HexKey] [B] [Rs]");
            return;
        }

        String inputPath = args[0];
        String outputPath = args.length >= 2 ? args[1] : defaultOutputName(inputPath, "_scrambled.bmp");
        String keyHex = args.length >= 3 ? args[2] : DEFAULT_KEY_HEX;
        int B = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_BLOCK_SIZE;
        int Rs = args.length >= 5 ? Integer.parseInt(args[4]) : DEFAULT_ROUNDS;

        byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);
        BufferedImage input = ImageIO.read(new File(inputPath));
        if (input == null) throw new IllegalArgumentException("Unreadable input image.");

        BufferedImage out = scramble(input, keyBytes, B, Rs);
        ImageIO.write(out, "bmp", new File(outputPath));

        System.out.println("Scrambling complete.");
        System.out.println("Input : " + inputPath);
        System.out.println("Output: " + outputPath);
        System.out.println("B=" + B + ", Rs=" + Rs);
    }

    public static BufferedImage scramble(BufferedImage img, byte[] keyBytes, int B, int R) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        if (W % B != 0 || H % B != 0)
            throw new IllegalArgumentException("Image dimensions must be divisible by B.");

        BufferedImage current = copyImage(img);
        byte[] K = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_SCRAMBLE_INIT});

        for (int round = 1; round <= R; round++) {
            byte[] Hb = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_BLOCK});
            current = blockShuffle(current, B, Hb);

            byte[] Hc = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_COLUMN});
            current = colShuffle(current, Hc);

            byte[] Hr = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_ROW});
            current = rowShuffle(current, Hr);

            byte[] Hp = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_PIXEL});
            current = pixelShuffle(current, Hp);

            K = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_UPDATE});
        }
        return current;
    }

    public static BufferedImage unscramble(BufferedImage img, byte[] keyBytes, int B, int R) throws Exception {
        byte[][] Hb = new byte[R+1][], Hc = new byte[R+1][], Hr = new byte[R+1][], Hp = new byte[R+1][];
        byte[] K = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_SCRAMBLE_INIT});

        for (int round = 1; round <= R; round++) {
            Hb[round] = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_BLOCK});
            Hc[round] = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_COLUMN});
            Hr[round] = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_ROW});
            Hp[round] = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_PIXEL});
            K = CipherUtil.sha256(K, CipherUtil.be32(round), new byte[]{CipherUtil.TAG_SCRAMBLE_UPDATE});
        }

        BufferedImage current = copyImage(img);
        for (int round = R; round >= 1; round--) {
            current = pixelShuffleInverse(current, Hp[round]);
            current = rowShuffleInverse(current, Hr[round]);
            current = colShuffleInverse(current, Hc[round]);
            current = blockShuffleInverse(current, B, Hb[round]);
        }
        return current;
    }

    static BufferedImage blockShuffle(BufferedImage img, int B, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        List<int[]> blocks = new ArrayList<>();

        for (int y = 0; y < H; y += B) {
            for (int x = 0; x < W; x += B) {
                int[] block = new int[B*B];
                int k = 0;
                for (int dy = 0; dy < B; dy++)
                    for (int dx = 0; dx < B; dx++)
                        block[k++] = img.getRGB(x+dx, y+dy);
                blocks.add(block);
            }
        }

        int[] perm = getPermutation(blocks.size(), seed);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

        int p = 0;
        for (int y = 0; y < H; y += B) {
            for (int x = 0; x < W; x += B) {
                int[] block = blocks.get(perm[p++]);
                int k = 0;
                for (int dy = 0; dy < B; dy++)
                    for (int dx = 0; dx < B; dx++)
                        out.setRGB(x+dx, y+dy, block[k++]);
            }
        }
        return out;
    }

    static BufferedImage blockShuffleInverse(BufferedImage img, int B, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        int nBlocks = (W/B)*(H/B);
        int[] perm = getPermutation(nBlocks, seed);
        int[] inv = invertPermutation(perm);

        List<int[]> blocks = new ArrayList<>();
        for (int y = 0; y < H; y += B) {
            for (int x = 0; x < W; x += B) {
                int[] block = new int[B*B];
                int k = 0;
                for (int dy = 0; dy < B; dy++)
                    for (int dx = 0; dx < B; dx++)
                        block[k++] = img.getRGB(x+dx, y+dy);
                blocks.add(block);
            }
        }

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int p = 0;
        for (int y = 0; y < H; y += B) {
            for (int x = 0; x < W; x += B) {
                int[] block = blocks.get(inv[p++]);
                int k = 0;
                for (int dy = 0; dy < B; dy++)
                    for (int dx = 0; dx < B; dx++)
                        out.setRGB(x+dx, y+dy, block[k++]);
            }
        }
        return out;
    }

    static BufferedImage colShuffle(BufferedImage img, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        int[] perm = getPermutation(W, seed);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                out.setRGB(x, y, img.getRGB(perm[x], y));
        return out;
    }

    static BufferedImage colShuffleInverse(BufferedImage img, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        int[] inv = invertPermutation(getPermutation(W, seed));
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                out.setRGB(x, y, img.getRGB(inv[x], y));
        return out;
    }

    static BufferedImage rowShuffle(BufferedImage img, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        int[] perm = getPermutation(H, seed);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                out.setRGB(x, y, img.getRGB(x, perm[y]));
        return out;
    }

    static BufferedImage rowShuffleInverse(BufferedImage img, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight();
        int[] inv = invertPermutation(getPermutation(H, seed));
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                out.setRGB(x, y, img.getRGB(x, inv[y]));
        return out;
    }

    static BufferedImage pixelShuffle(BufferedImage img, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight(), N = W*H;
        int[] perm = getPermutation(N, seed);
        int[] flat = new int[N];
        for (int i = 0; i < N; i++) flat[i] = img.getRGB(i%W, i/W);

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < N; i++) out.setRGB(i%W, i/W, flat[perm[i]]);
        return out;
    }

    static BufferedImage pixelShuffleInverse(BufferedImage img, byte[] seed) throws Exception {
        int W = img.getWidth(), H = img.getHeight(), N = W*H;
        int[] inv = invertPermutation(getPermutation(N, seed));
        int[] flat = new int[N];
        for (int i = 0; i < N; i++) flat[i] = img.getRGB(i%W, i/W);

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < N; i++) out.setRGB(i%W, i/W, flat[inv[i]]);
        return out;
    }

    static int[] getPermutation(int n, byte[] seed) throws Exception {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;

        for (int i = n-1; i > 0; i--) {
            byte[] d = CipherUtil.sha256(seed, CipherUtil.be32(i));
            int j = CipherUtil.positiveIntFromFirst4Bytes(d) % (i+1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        return perm;
    }

    static int[] invertPermutation(int[] perm) {
        int[] inv = new int[perm.length];
        for (int i = 0; i < perm.length; i++) inv[perm[i]] = i;
        return inv;
    }

    static BufferedImage copyImage(BufferedImage input) {
        int W = input.getWidth(), H = input.getHeight();
        BufferedImage copy = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                copy.setRGB(x, y, input.getRGB(x, y) & 0xffffff);
        return copy;
    }

    static String defaultOutputName(String inputPath, String suffix) {
        File f = new File(inputPath);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(f.getAbsoluteFile().getParentFile(), base + suffix).getPath();
    }
}
