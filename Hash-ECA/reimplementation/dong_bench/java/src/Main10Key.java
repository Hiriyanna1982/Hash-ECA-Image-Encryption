package dong;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import javax.imageio.ImageIO;

/**
 * 10-key × 10-image (100-trial) driver for entropy, r_avg, NPCR, and UACI.
 * Timing is measured separately by the single-key benchmark.
 *
 * The 10 fixed 256-bit keys are read from dong_keys.csv. The benchmark
 * timestamp remains fixed at 20260101000000 for reproducibility.
 */
public final class Main10Key {

    static final long TIMESTAMP = 20260101000000L;
    static final int PERTURB_ROW = 255, PERTURB_COL = 255;

    public static void main(String[] args) throws Exception {
        String inDir = args.length > 0 ? args[0] : "images_locked";
        String outDir = args.length > 1 ? args[1] : "results10";
        String keysCsv = args.length > 2 ? args[2] : "dong_keys.csv";
        new File(outDir).mkdirs();

        String[] names = {"Airplane","Baboon","Barbara","Boats","House","Lena","Monarch","Pepper","Sailboat","Tiffany"};

        List<String> keyHexList = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(keysCsv))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                keyHexList.add(line.split(",")[1].trim());
            }
        }

        int total = names.length * keyHexList.size();
        int done = 0;
        for (String name : names) {
            String path = inDir + "/" + name + ".bmp";
            BufferedImage original = ImageIO.read(new File(path));
            int[] plain = ImageVector.flattenRGB(original);
            int M = original.getHeight(), N = original.getWidth();

            BufferedImage perturbed = copyImage(original);
            int rgb = perturbed.getRGB(PERTURB_COL, PERTURB_ROW) & 0xffffff;
            int pr = (rgb>>16)&0xff, pg=(rgb>>8)&0xff, pb=rgb&0xff;
            pr = (pr+1)&0xff; pg=(pg+1)&0xff; pb=(pb+1)&0xff;
            perturbed.setRGB(PERTURB_COL, PERTURB_ROW, (pr<<16)|(pg<<8)|pb);
            int[] plain2 = ImageVector.flattenRGB(perturbed);

            int keyIdx = 0;
            for (String keyHex : keyHexList) {
                keyIdx++;
                boolean[] Ki = BitUtil.hexToBits(keyHex);

                boolean[] Ks = KeySeedGenerator.generate(Ki, TIMESTAMP, plain);
                int[] cipher = CipherStage.encrypt(Ks, plain);

                boolean[] Ks2 = KeySeedGenerator.generate(Ki, TIMESTAMP, plain2);
                int[] cipher2 = CipherStage.encrypt(Ks2, plain2);

                ImageIO.write(ImageVector.unflattenRGB(cipher, M, N), "bmp",
                        new File(outDir + "/" + name + "_k" + keyIdx + "_C1.bmp"));
                ImageIO.write(ImageVector.unflattenRGB(cipher2, M, N), "bmp",
                        new File(outDir + "/" + name + "_k" + keyIdx + "_C2.bmp"));

                done++;
                if (done % 10 == 0 || done == total)
                    System.out.println("[dong-10key] " + done + "/" + total);
            }
        }
        System.out.println("DONG 10-KEY RUN COMPLETE.");
    }

    static BufferedImage copyImage(BufferedImage input) {
        int W=input.getWidth(), H=input.getHeight();
        BufferedImage copy = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<H;y++)
            for (int x=0;x<W;x++)
                copy.setRGB(x,y,input.getRGB(x,y)&0xffffff);
        return copy;
    }
}
