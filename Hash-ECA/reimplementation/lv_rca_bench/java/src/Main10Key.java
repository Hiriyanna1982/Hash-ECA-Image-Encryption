package lvrca;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * 10-key × 10-image (100-trial) driver for entropy, r_avg, NPCR, and UACI.
 * Timing is measured separately by the single-key benchmark.
 *
 * The 10 fixed 256-bit keys are read from lv_keys.csv.
 */
public final class Main10Key {

    static final int PERTURB_ROW = 255, PERTURB_COL = 255;

    public static void main(String[] args) throws Exception {
        String inDir = args.length > 0 ? args[0] : "images_locked";
        String outDir = args.length > 1 ? args[1] : "results10";
        String keysCsv = args.length > 2 ? args[2] : "lv_keys.csv";
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
            for (String KEY : keyHexList) {
                keyIdx++;
                byte[][] c1 = RgbCipher.encryptColor(img.planes, m, n, KEY);
                byte[][] c2 = RgbCipher.encryptColor(perturbed, m, n, KEY);

                BmpIO.write(outDir + "/" + name + "_k" + keyIdx + "_C1.bmp", m, n, c1);
                BmpIO.write(outDir + "/" + name + "_k" + keyIdx + "_C2.bmp", m, n, c2);

                done++;
                if (done % 10 == 0 || done == total)
                    System.out.println("[lv-10key] " + done + "/" + total);
            }
        }
        System.out.println("LV/RCA 10-KEY RUN COMPLETE.");
    }
}
