package dong;

/**
 * Sec. 4.1.1: secret key seed Ks generation (Steps 1-8), fully resolved per
 * ASSUMPTIONS.md. Key resolved decisions baked in here:
 *
 * - Eq. (13)/(14) use denominator 2^48 (NOT 2^48-1) -- confirmed by exact
 *   PDF text extraction, distinct from Eq. (21)'s 2^48-1 in the cipher stage.
 * - S0 = {Ki'(1:48), Ki(49:256)} for EVERY PRCML-HECA instantiation in this
 *   stage (per-block model in Step 7, and every box of the series-wound
 *   model in Step 8) -- fixed, reused unchanged throughout.
 * - A-Dong-20: every fresh (x0,y0) pair -- the per-block pair from Step 6,
 *   and every series-wound box's (x0,y0) pair in Step 8 -- is advanced 20
 *   bare-CSM iterations before its model is run.
 * - Step 7's "output ki (256-bit)" is read as a length-256 REAL-VALUED
 *   vector (the model's y-array after 16 iterations), not a literal 256-bit
 *   binary vector: this is the only reading consistent with Step 8's
 *   continuous series-wound wiring ("y1 of the former is x0 of the latter")
 *   and Eq. (19)'s mod-1 summation, which only makes sense on continuous
 *   values. Disclosed in ASSUMPTIONS.md.
 * - Series-wound model (Sec. 4.1.1 Step 8 / Fig. 16): following the literal wiring in Fig. 16, this
 *   is a SINGLE pass of q boxes (q = number of 512-blocks), each box
 *   "Iterated for once" -- matching Fig. 16's literal wiring -- rather than
 *   two full passes. This conflicts with Sec. 4.3.2's complexity-analysis
 *   text ("the model is iterated for 18*M*N/512 times in total", which
 *   algebraically requires 16q (Step 7) + 2q (series-wound) = 18q). That
 *   conflict is documented in Dong2022_ASSUMPTIONS.md. This implementation
 *   follows the literal figure (q iterations total for
 *   the series-wound stage), not the complexity formula.
 *   Box wiring (confirmed by close inspection of Fig. 16):
 *     box 1:      x0 = k_1,                 y0 = k_2
 *     box i(>1):  x0 = (box i-1's y-output), y0 = k_{i+1}, circularly
 *                 wrapping to k_1 for the last box (box q's y0 = k_1).
 *   Each box's own y-output (after its 20-iter burn-in + 1 model iteration)
 *   is tapped as k_i'.
 * - Eq. (19): typeset as K's = sum(k_i) mod 1 (unprimed k_i), but the
 *   preceding sentence explicitly says the series-wound outputs k_i' are
 *   what get summed here ("After the operation of the model, k_i' ... can
 *   be obtained. And then, the key seed Ks is calculated as: [Eq 19]").
 *   Summing the unprimed, pre-series-wound k_i would make Step 8 pointless.
 *   Treated as a paper typo; this implementation sums k_i' as the prose
 *   describes. Documented in Dong2022_ASSUMPTIONS.md.
 * - Eq. (19) is applied literally on the model's native [0,2pi)
 *   output -- Ks' = (sum_i k_i') mod 1, with NO prior division by 2*pi. The
 *   "mod 1" fractional-part operation is exactly what Eq. (19) specifies;
 *   an earlier version of this file incorrectly normalized by 2*pi first
 *   (over-generalizing the /2pi correction that Eq. (23) does require).
 */
public final class KeySeedGenerator {
    private KeySeedGenerator() {}

    private static final double POW2_48 = Math.pow(2, 48);

    public static final class ControlParams {
        public final double K, eps;
        public final boolean[] S0; // 256 bits
        public final int Nr1, Nr2;
        public ControlParams(double K, double eps, boolean[] S0, int Nr1, int Nr2) {
            this.K = K; this.eps = eps; this.S0 = S0; this.Nr1 = Nr1; this.Nr2 = Nr2;
        }
    }

    /** Steps 1-4: derive the control parameters shared by every PRCML-HECA usage in this stage. */
    public static ControlParams deriveControlParams(boolean[] Ki256, long timestampDecimal) {
        if (Ki256.length != 256) throw new IllegalArgumentException("Ki must be 256 bits");

        // Step 1
        boolean[] Ki_1_48 = BitUtil.slice1(Ki256, 1, 48);
        long Kd = BitUtil.bin2dec(Ki_1_48);
        long Td = timestampDecimal;

        // Step 2 (Eq. 13)
        long xored = Kd ^ Td;
        boolean[] KiPrime_1_48 = BitUtil.dec2bin(xored, 48);

        // Step 3 (Eq. 14) -- denominator 2^48
        double K = 8.0 * (1.0 + (double) BitUtil.bin2dec(KiPrime_1_48) / POW2_48);
        boolean[] Ki_49_96 = BitUtil.slice1(Ki256, 49, 96);
        double eps = 0.5 * ((double) BitUtil.bin2dec(Ki_49_96) / POW2_48);

        // Step 4
        boolean[] Ki_49_256 = BitUtil.slice1(Ki256, 49, 256); // 208 bits
        boolean[] S0 = BitUtil.concat(KiPrime_1_48, Ki_49_256); // 48+208 = 256 bits

        boolean[] Ki_193_224 = BitUtil.slice1(Ki256, 193, 224);
        boolean[] Ki_225_256 = BitUtil.slice1(Ki256, 225, 256);
        int Nr1 = (int) (BitUtil.bin2dec(Ki_193_224) % 34) + 1;
        int Nr2 = (int) (BitUtil.bin2dec(Ki_225_256) % 34) + 1;
        if (Nr1 == Nr2) {
            Nr2 = (Nr1 + 1) % 34 + 1; // Eq. 16
        }

        return new ControlParams(K, eps, S0, Nr1, Nr2);
    }

    /**
     * Full key-seed generation (Steps 5-8 + Eq. 19/20).
     * @param plainVector column-major-flattened plaintext, R-plane then G then B for color
     *                    (or the plain gray plane), raw pixel values 0..255, length must be
     *                    a multiple of 512.
     */
    public static boolean[] generate(boolean[] Ki256, long timestampDecimal, int[] plainVector) {
        ControlParams cp = deriveControlParams(Ki256, timestampDecimal);
        double K = cp.K, eps = cp.eps;
        boolean[] S0 = cp.S0;
        int Nr1 = cp.Nr1, Nr2 = cp.Nr2;

        int n3 = plainVector.length;
        if (n3 % 512 != 0) throw new IllegalArgumentException("plainVector length must be a multiple of 512");
        int q = n3 / 512;

        // Step 5 (Eq. 17): normalize to [0, 2pi)
        double[] Pn = new double[n3];
        for (int i = 0; i < n3; i++) Pn[i] = (plainVector[i] / 255.0) * Csm.TWO_PI;

        // Steps 6-7: per-block model, 16 iterations each -> ki (length-256 real vectors)
        double[][] ki = new double[q][];
        for (int b = 0; b < q; b++) {
            double[] x0 = new double[256];
            double[] y0 = new double[256];
            System.arraycopy(Pn, b * 512, x0, 0, 256);
            System.arraycopy(Pn, b * 512 + 256, y0, 0, 256);

            double[][] burned = ModelInit.burnIn20(x0, y0, K); // A-Dong-20
            CoupledLattice lattice = new CoupledLattice(burned[0], burned[1], S0, K, eps, Nr1, Nr2);
            lattice.iterate(16); // Step 7
            ki[b] = lattice.getY().clone();
        }

        // Step 8: series-wound model, single pass of q boxes (following the documented implementation choice).
        double[][] kPrime = new double[q][];
        double[] prevY = null;
        for (int idx = 0; idx < q; idx++) {
            double[] x0Box = (idx == 0) ? ki[0].clone() : prevY;
            double[] y0Box = ki[(idx + 1) % q]; // k_{i+1}, circularly wraps to k_1 for the last box

            double[][] burned = ModelInit.burnIn20(x0Box, y0Box, K); // A-Dong-20
            CoupledLattice lattice = new CoupledLattice(burned[0], burned[1], S0, K, eps, Nr1, Nr2);
            lattice.iterate(1); // "Iterated for once"
            kPrime[idx] = lattice.getY().clone();
            prevY = kPrime[idx];
        }

        // Eq. 19 (summing k_i' per the prose; see class-level note on the typeset k_i vs k_i' issue).
        // no 2*pi normalization is applied here. Eq. (19) literally defines
        // Ks' = (sum_i k_i') mod 1, using the model's native [0,2pi)-range output directly; the
        // "mod 1" operation itself extracts the fractional part of the raw sum, so no prior
        // [0,2pi]->[0,1] rescaling is required (that normalization is a separate, necessary
        // correction for Eq. (23) elsewhere, whose floor(*256) explicitly requires a [0,1) input --
        // it does not apply here).
        double[] KsPrime = new double[256];
        for (int j = 0; j < 256; j++) {
            double sum = 0.0;
            for (int idx = 0; idx < q; idx++) sum += kPrime[idx][j];
            double frac = sum - Math.floor(sum); // "mod 1": fractional part
            KsPrime[j] = frac;
        }

        // Eq. 20
        boolean[] Ks = new boolean[256];
        for (int j = 0; j < 256; j++) Ks[j] = KsPrime[j] >= 0.5;
        return Ks;
    }
}
