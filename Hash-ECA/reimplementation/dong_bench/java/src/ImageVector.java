package dong;

import java.awt.image.BufferedImage;

/**
 * Column-major (MATLAB-style) flattening of an RGB image into a single
 * M*N*3-length vector: R plane fully flattened column-major first, then G,
 * then B. Disclosed assumption (the paper does not specify serialization
 * order for Sec. 4.1.3's combined-vector color cipher) -- well-justified
 * given the paper's MATLAB reference implementation.
 *
 * index = r + c*M + ch*M*N   (0-indexed: r=0..M-1, c=0..N-1, ch=0..2)
 */
public final class ImageVector {
    private ImageVector() {}

    /** Flattens an RGB BufferedImage (width=N, height=M) into a length-M*N*3 vector, values 0..255. */
    public static int[] flattenRGB(BufferedImage img) {
        int N = img.getWidth();  // columns
        int M = img.getHeight(); // rows
        int[] out = new int[M * N * 3];
        for (int c = 0; c < N; c++) {
            for (int r = 0; r < M; r++) {
                int rgb = img.getRGB(c, r) & 0xffffff;
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                int base = r + c * M;
                out[base] = red;
                out[base + M * N] = green;
                out[base + 2 * M * N] = blue;
            }
        }
        return out;
    }

    /** Inverse of flattenRGB: writes a length-M*N*3 vector back into an M(rows) x N(cols) RGB image. */
    public static BufferedImage unflattenRGB(int[] vec, int M, int N) {
        BufferedImage img = new BufferedImage(N, M, BufferedImage.TYPE_INT_RGB);
        for (int c = 0; c < N; c++) {
            for (int r = 0; r < M; r++) {
                int base = r + c * M;
                int red = vec[base] & 0xff;
                int green = vec[base + M * N] & 0xff;
                int blue = vec[base + 2 * M * N] & 0xff;
                img.setRGB(c, r, (red << 16) | (green << 8) | blue);
            }
        }
        return img;
    }
}
