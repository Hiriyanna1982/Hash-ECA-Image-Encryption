package dong;

/**
 * The bare Chirikov Standard Map (Eq. 4):
 *   x_{n+1} = (x_n + K*sin(y_n)) mod 2pi
 *   y_{n+1} = (y_n + x_{n+1})    mod 2pi
 * Sequential (Gauss-Seidel style): y's update uses the just-computed x_{n+1},
 * not the old x_n. Confirmed against the paper's typeset Eq. (4).
 */
public final class Csm {
    private Csm() {}

    public static final double TWO_PI = 2.0 * Math.PI;

    public static double mod2pi(double v) {
        double r = v % TWO_PI;
        if (r < 0) r += TWO_PI;
        return r;
    }

    /** One CSM step. Returns {xNext, yNext}. */
    public static double[] step(double x, double y, double K) {
        double xNext = mod2pi(x + K * Math.sin(y));
        double yNext = mod2pi(y + xNext);
        return new double[]{xNext, yNext};
    }

    /** Applies the bare CSM step 'times' times in sequence, returning the final {x,y}. */
    public static double[] iterate(double x, double y, double K, int times) {
        double cx = x, cy = y;
        for (int t = 0; t < times; t++) {
            double[] r = step(cx, cy, K);
            cx = r[0];
            cy = r[1];
        }
        return new double[]{cx, cy};
    }
}
