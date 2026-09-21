package liu;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * 10-key × 10-image (100-trial) driver for entropy, r_avg, NPCR, and UACI.
 * Timing is measured separately by the single-key benchmark.
 *
 * The 10 fixed parameter sets are read from liu_keys.csv.
 */
public final class Main10Key {

    static final int PERTURB_ROW = 255, PERTURB_COL = 255;

    static final class KeyRow {
        double x1, y1, x2, y2, a, b;
    }

    public static void main(String[] args) throws Exception {
        String inDir = args.length > 0 ? args[0] : "images_locked";
        String outDir = args.length > 1 ? args[1] : "results10";
        String keysCsv = args.length > 2 ? args[2] : "liu_keys.csv";
        new File(outDir).mkdirs();

        String[] names = {"Airplane","Baboon","Barbara","Boats","House","Lena","Monarch","Pepper","Sailboat","Tiffany"};

        List<KeyRow> keys = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(keysCsv))) {
            if (sc.hasNextLine()) sc.nextLine(); // header
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                KeyRow k = new KeyRow();
                k.x1 = Double.parseDouble(p[1]);
                k.y1 = Double.parseDouble(p[2]);
                k.x2 = Double.parseDouble(p[3]);
                k.y2 = Double.parseDouble(p[4]);
                k.a = Double.parseDouble(p[5]);
                k.b = Double.parseDouble(p[6]);
                keys.add(k);
            }
        }

        int total = names.length * keys.size();
        int done = 0;
        for (String name : names) {
            String path = inDir + "/" + name + ".bmp";
            BmpIO.Image img = BmpIO.read(path);
            int m = img.m, n = img.n;

            byte[][] perturbed = new byte[][]{
                    img.planes[0].clone(), img.planes[1].clone(), img.planes[2].clone()
            };
            int idx = PERTURB_ROW * n + PERTURB_COL;
            for (int c = 0; c < 3; c++) {
                perturbed[c][idx] = (byte) (((perturbed[c][idx] & 0xFF) + 1) & 0xFF);
            }

            int keyIdx = 0;
            for (KeyRow k : keys) {
                keyIdx++;
                LiuCipher.EncryptedChannel[] enc1 = LiuCipher.encryptColor(img.planes, m, n, k.x1, k.y1, k.x2, k.y2, k.a, k.b);
                LiuCipher.EncryptedChannel[] enc2 = LiuCipher.encryptColor(perturbed, m, n, k.x1, k.y1, k.x2, k.y2, k.a, k.b);

                byte[][] c1 = new byte[][]{enc1[0].cipher, enc1[1].cipher, enc1[2].cipher};
                byte[][] c2 = new byte[][]{enc2[0].cipher, enc2[1].cipher, enc2[2].cipher};

                BmpIO.write(outDir + "/" + name + "_k" + keyIdx + "_C1.bmp", m, n, c1);
                BmpIO.write(outDir + "/" + name + "_k" + keyIdx + "_C2.bmp", m, n, c2);

                done++;
                if (done % 10 == 0 || done == total)
                    System.out.println("[liu-10key] " + done + "/" + total);
            }
        }
        System.out.println("LIU 10-KEY RUN COMPLETE.");
    }
}
