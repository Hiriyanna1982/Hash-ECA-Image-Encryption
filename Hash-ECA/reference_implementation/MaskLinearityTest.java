package white;

import java.util.*;

// Addresses Reviewer 2 Comment 1: a direct, exhaustive test of whether the
// mask-generation mapping f(seed) is affine over GF(2), tested at the mask
// level in isolation (not through the whole cipher). Domain is 8 bits, so
// this is exhaustive over all 256x256 = 65,536 (a,b) pairs, not sampled.
public class MaskLinearityTest {

    // f is affine over GF(2) iff f(a XOR b) == f(a) XOR f(b) XOR f(0) for all a,b.
    static int deviationCount(java.util.function.IntUnaryOperator f) {
        int f0 = f.applyAsInt(0);
        int deviations = 0;
        for (int a = 0; a < 256; a++) {
            int fa = f.applyAsInt(a);
            for (int b = 0; b < 256; b++) {
                int fb = f.applyAsInt(b);
                int predicted = fa ^ fb ^ f0;
                int actual = f.applyAsInt(a ^ b);
                if (actual != predicted) deviations++;
            }
        }
        return deviations;
    }

    public static void main(String[] args) {
        int totalPairs = 256 * 256;

        System.out.println("=== Mask-mapping GF(2) affine test (exhaustive, " + totalPairs + " pairs) ===\n");

        // No-ECA baseline: mask = seed (identity)
        int devIdentity = deviationCount(seed -> seed);
        System.out.printf("No-ECA baseline (Mask = Eseed, identity map): %d / %d deviations (%.4f%%) -- affine by construction%n",
            devIdentity, totalPairs, 100.0 * devIdentity / totalPairs);

        System.out.println();

        // The 4 selected ECA rules
        int[] rules = {30, 45, 106, 184};
        for (int rule : rules) {
            int dev = deviationCount(seed -> DiffusionV2.ecaOneStep(seed, rule));
            System.out.printf("ECA rule %3d (Mask = OneStepECA(Eseed, %d)):   %d / %d deviations (%.4f%%) -- nonlinear%n",
                rule, rule, dev, totalPairs, 100.0 * dev / totalPairs);
        }
    }
}
