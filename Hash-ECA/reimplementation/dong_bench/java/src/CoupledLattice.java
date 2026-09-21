package dong;

/**
 * The full PRCML-HECA model with perturbation (Eq. 7 + Eq. 8), L=256 lattices.
 *
 * Eq. 7 (confirmed by exact PDF text extraction):
 *   (x_{n+1}(i), y_{n+1}(i)) =
 *       (1-e) * f(x_n(i), y_n(i))
 *     + (e/2) * ( f(x_n(a), y_n(a)) + f(x_n(b), y_n(b)) )
 *     + p(Sn, i)                                            , all mod 2pi
 * where f is the bare CSM step (Csm.step), a and b are the pseudo-random
 * coupled-neighbor indices for lattice i (found from Sn), and p(Sn,i) is a
 * scalar perturbation term.
 *
 * Documented implementation assumption: p(Sn,i) is a
 * scalar and is added identically to BOTH the x and y components of the
 * vector sum (the paper's Eq. 7 does not separately specify a p_x vs p_y).
 *
 * Coupled-neighbor selection a(i), b(i) (Fig. 7, confirmed by text):
 *   a = nearest index with S(a)=1, scanning circularly LEFTWARD from i
 *       (i-1, i-2, ...); b = nearest index with S(b)=1, scanning circularly
 *       RIGHTWARD from i (i+1, i+2, ...). Never equal to i.
 *
 * Perturbation (Eq. 8, confirmed by text, m = L/2 = 128 1-indexed =>
 * 0-indexed window [112,143] inclusive, 32 bits):
 *   p(Sn,i) = bin2dec(Sn(m-15:m+16)) / (2^32 - 1) * 2pi * (Sn(i) - 0.5)
 *
 * S evolves via the HECA transition (Eq. 5) once per model iteration: the
 * Sn used to pick a,b and p for producing (x_{n+1},y_{n+1}) is the state
 * BEFORE that iteration's HECA step; S is then advanced to S_{n+1} for the
 * next iteration.
 */
public final class CoupledLattice {

    public static final int L = HecaRules.L;
    private static final long WINDOW_DENOM = (1L << 32) - 1L; // 2^32 - 1
    private static final int M_MID = L / 2; // 128 (1-indexed)
    private static final int WIN_START_1INDEXED = M_MID - 15; // 113
    // 0-indexed window start = WIN_START_1INDEXED - 1 = 112, length 32

    private double[] x;
    private double[] y;
    private boolean[] S;
    private final double K;
    private final double eps;
    private final int r1rule;
    private final int r2rule;

    public CoupledLattice(double[] x0, double[] y0, boolean[] S0, double K, double eps, int Nr1, int Nr2) {
        if (x0.length != L || y0.length != L || S0.length != L)
            throw new IllegalArgumentException("expected length-" + L + " vectors");
        this.x = x0.clone();
        this.y = y0.clone();
        this.S = S0.clone();
        this.K = K;
        this.eps = eps;
        this.r1rule = HecaRules.ruleFor(Nr1);
        this.r2rule = HecaRules.ruleFor(Nr2);
    }

    public double[] getX() { return x; }
    public double[] getY() { return y; }
    public boolean[] getS() { return S; }

    /** Finds nearest circular index leftward (a) / rightward (b) of i with S(idx)==1, excluding i itself. */
    private static int findNearest(boolean[] S, int i, boolean leftward) {
        int n = S.length;
        for (int step = 1; step < n; step++) {
            int idx = leftward ? Math.floorMod(i - step, n) : Math.floorMod(i + step, n);
            if (S[idx]) return idx;
        }
        // Degenerate fallback (S all-zero): never expected with real key/plaintext-derived
        // states, but avoid an infinite loop / undefined coupling.
        return i;
    }

    private double perturbation(boolean[] Sn, int i) {
        long win = BitUtil.bin2dec(Sn, WIN_START_1INDEXED - 1, 32);
        double frac = (double) win / (double) WINDOW_DENOM;
        double sign = (Sn[i] ? 1.0 : 0.0) - 0.5;
        return frac * Csm.TWO_PI * sign;
    }

    /** Advances the model by exactly one iteration (Eq. 7 sweep + one HECA step). */
    public void stepOnce() {
        boolean[] Sn = S; // Sn = state at time n, used for this iteration's coupling/perturbation
        double[] newX = new double[L];
        double[] newY = new double[L];

        // Precompute f(x_n(i), y_n(i)) for all i once.
        double[] fx = new double[L];
        double[] fy = new double[L];
        for (int i = 0; i < L; i++) {
            double[] f = Csm.step(x[i], y[i], K);
            fx[i] = f[0];
            fy[i] = f[1];
        }

        for (int i = 0; i < L; i++) {
            int a = findNearest(Sn, i, true);
            int b = findNearest(Sn, i, false);
            double p = perturbation(Sn, i);

            double vx = (1.0 - eps) * fx[i] + (eps / 2.0) * (fx[a] + fx[b]) + p;
            double vy = (1.0 - eps) * fy[i] + (eps / 2.0) * (fy[a] + fy[b]) + p;
            newX[i] = Csm.mod2pi(vx);
            newY[i] = Csm.mod2pi(vy);
        }

        x = newX;
        y = newY;
        S = HecaRules.step(Sn, r1rule, r2rule); // advance to S_{n+1}
    }

    /** Advances the model by 'times' iterations. */
    public void iterate(int times) {
        for (int t = 0; t < times; t++) stepOnce();
    }
}
