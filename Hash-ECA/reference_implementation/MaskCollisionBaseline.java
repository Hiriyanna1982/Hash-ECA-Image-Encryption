package white;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

// Computes the theoretical baseline referenced in Section 6.5: "close to the
// exhaustively computed independent-control-byte mask-collision baseline of
// 0.79%." The mask-generation mapping (position-mix XOR state byte, then
// one-step ECA under the selected rule) is NOT a bijection on its 8-bit
// domain, so the chance that two INDEPENDENT uniform-random control bytes
// produce the same mask at a given pixel position is not the naive 1/256 --
// it is the mapping's own collision probability, sum_v (count_v/256)^2,
// which this program computes exhaustively (all 256 candidate bytes) at
// every sampled pixel position, matching the granularity of Part A of
// AdaptiveControlStateAttack.java (every 50th pixel in scan order).
public class MaskCollisionBaseline {
    static final int[] POOL_C = {30, 45, 106, 184};
    static final int W = 512, H = 512;

    // exact collision probability at one pixel position, given its public Pmix value
    static double collisionProbabilityAt(int x, int y) {
        int posMix = ((x * x + 3 * x * y + y * y) ^ (x + y)) & 0xff;
        Map<Integer, Integer> outputCounts = new HashMap<>();
        for (int b = 0; b < 256; b++) {
            int eseed = posMix ^ b;
            int rule = POOL_C[b % POOL_C.length];
            int mask = DiffusionV2.ecaOneStep(eseed, rule);
            outputCounts.merge(mask, 1, Integer::sum);
        }
        double collisionProb = 0;
        for (int count : outputCounts.values()) {
            collisionProb += (count / 256.0) * (count / 256.0);
        }
        return collisionProb;
    }

    public static void main(String[] args) throws Exception {
        String outCsv = args.length > 0 ? args[0] : "mask_collision_baseline.csv";

        double total = 0;
        long count = 0;
        try (PrintWriter log = new PrintWriter(new FileWriter(outCsv))) {
            log.println("x,y,posMix,collisionProbabilityPct");
            int pixelIndex = 0;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    pixelIndex++;
                    if (pixelIndex % 50 != 0) continue; // same sampling granularity as Part A
                    int posMix = ((x * x + 3 * x * y + y * y) ^ (x + y)) & 0xff;
                    double p = collisionProbabilityAt(x, y);
                    log.println(x + "," + y + "," + posMix + "," + (p * 100));
                    total += p;
                    count++;
                }
            }
        }
        double avgPct = 100.0 * total / count;
        System.out.println("Sampled positions: " + count);
        System.out.printf("Average theoretical mask-collision probability: %.4f%%%n", avgPct);
        System.out.println("(naive, incorrect 1/256 baseline would be: " + (100.0 / 256) + "%)");
    }
}
