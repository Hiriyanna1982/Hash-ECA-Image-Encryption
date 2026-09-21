package liu;

/**
 * 2D-CICM chaotic map (Eq. 3 of the paper):
 *   x_{n+1} = sin( a*pi^2 / ( cos(b*acos(x_n)) * (1 - cos(b*acos(y_n))) ) )
 *   y_{n+1} = sin( b*pi^2 / ( cos(a*acos(y_n)) * (1 - cos(a*acos(x_n))) ) )
 */
public final class CICM {
    private final double a, b;
    private double x, y;

    public CICM(double x0, double y0, double a, double b) {
        this.x = x0;
        this.y = y0;
        this.a = a;
        this.b = b;
    }

    /** Advance one iteration, returning the new (x,y) as a 2-element array. */
    public double[] next() {
        double ax = Math.acos(clamp(x));
        double ay = Math.acos(clamp(y));
        double nx = Math.sin((a * Math.PI * Math.PI) / (Math.cos(b * ax) * (1.0 - Math.cos(b * ay))));
        double ny = Math.sin((b * Math.PI * Math.PI) / (Math.cos(a * ay) * (1.0 - Math.cos(a * ax))));
        this.x = nx;
        this.y = ny;
        return new double[]{nx, ny};
    }

    private static double clamp(double v) {
        // guard against tiny FP drift outside [-1,1] which would make acos NaN
        if (v > 1.0) return 1.0;
        if (v < -1.0) return -1.0;
        return v;
    }

    /**
     * Iterate z+count times, discard the first z, return the remaining `count`
     * (x,y) pairs as two parallel arrays [xs, ys].
     */
    public static double[][] generate(double x0, double y0, double a, double b, int z, int count) {
        CICM m = new CICM(x0, y0, a, b);
        for (int i = 0; i < z; i++) m.next();
        double[] xs = new double[count];
        double[] ys = new double[count];
        for (int i = 0; i < count; i++) {
            double[] v = m.next();
            xs[i] = v[0];
            ys[i] = v[1];
        }
        return new double[][]{xs, ys};
    }
}
