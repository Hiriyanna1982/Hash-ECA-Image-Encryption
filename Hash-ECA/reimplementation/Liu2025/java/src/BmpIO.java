package liu;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class BmpIO {

    public static final class Image {
        public final int m, n; // rows, cols
        public final byte[][] planes; // [0]=R,[1]=G,[2]=B, each m*n row-major
        public Image(int m, int n, byte[][] planes) { this.m = m; this.n = n; this.planes = planes; }
    }

    public static Image read(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        int n = img.getWidth();
        int m = img.getHeight();
        byte[] r = new byte[m * n], g = new byte[m * n], b = new byte[m * n];
        for (int y = 0; y < m; y++) {
            for (int x = 0; x < n; x++) {
                int rgb = img.getRGB(x, y);
                r[y * n + x] = (byte) ((rgb >> 16) & 0xFF);
                g[y * n + x] = (byte) ((rgb >> 8) & 0xFF);
                b[y * n + x] = (byte) (rgb & 0xFF);
            }
        }
        return new Image(m, n, new byte[][]{r, g, b});
    }

    public static void write(String path, int m, int n, byte[][] planes) throws IOException {
        BufferedImage img = new BufferedImage(n, m, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < m; y++) {
            for (int x = 0; x < n; x++) {
                int r = planes[0][y * n + x] & 0xFF;
                int g = planes[1][y * n + x] & 0xFF;
                int b = planes[2][y * n + x] & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        String fmt = "bmp";
        if (!ImageIO.write(img, fmt, new File(path))) {
            throw new IOException("No BMP writer available");
        }
    }
}
