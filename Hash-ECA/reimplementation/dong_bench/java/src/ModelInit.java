package dong;

/**
 * Shared lattice-initialization helpers used by all three PRCML-HECA usages
 * in this cipher (key-seed per-block model, series-wound model, and the
 * final cipher-stage model), following the documented implementation assumptions:
 *
 * - A-Dong-20 (verbatim, from ASSUMPTIONS.md): "Following Sec. 3.1, after
 *   constructing the 256 lattice initial-state pairs using the 255-step CSM
 *   chain specified in Sec. 4.1.2 Step 2, each lattice pair is independently
 *   advanced by 20 CSM iterations before the coupled PRCML-HECA evolution
 *   begins. Sec. 4.1.2 does not restate this preprocessing step; this
 *   implementation treats the Sec. 3.1 model-level requirement as applicable
 *   to the encryption model." Applied consistently to ALL THREE PRCML-HECA
 *   usages (key-seed per-block model, series-wound model, and the final
 *   cipher-stage model) consistently across all three PRCML-HECA usages.
 *
 * - The 255-step lattice-seeding chain (Sec. 4.1.2 Step 2 only): lattice 1's
 *   (x0,y0) pair is advanced by the bare CSM 255 times, and each successive
 *   state seeds the next lattice: lattice[k] = CSM(lattice[k-1]).
 */
public final class ModelInit {
    private ModelInit() {}

    /** Applies the A-Dong-20 burn-in: 20 bare-CSM iterations, independently, to every lattice. */
    public static double[][] burnIn20(double[] x0, double[] y0, double K) {
        int n = x0.length;
        double[] bx = new double[n];
        double[] by = new double[n];
        for (int i = 0; i < n; i++) {
            double[] r = Csm.iterate(x0[i], y0[i], K, 20);
            bx[i] = r[0];
            by[i] = r[1];
        }
        return new double[][]{bx, by};
    }

    /**
     * Sec. 4.1.2 Step 2's 255-step lattice-seeding chain: given lattice 1's
     * (x0,y0), builds the full 256-length (x,y) arrays by repeatedly applying
     * the bare CSM map.
     */
    public static double[][] chain255(double x0Lattice1, double y0Lattice1, double K) {
        int n = HecaRules.L;
        double[] xArr = new double[n];
        double[] yArr = new double[n];
        xArr[0] = x0Lattice1;
        yArr[0] = y0Lattice1;
        double cx = x0Lattice1, cy = y0Lattice1;
        for (int k = 1; k < n; k++) {
            double[] r = Csm.step(cx, cy, K);
            cx = r[0];
            cy = r[1];
            xArr[k] = cx;
            yArr[k] = cy;
        }
        return new double[][]{xArr, yArr};
    }
}
