package dong;

/**
 * Table 3 (the LUT of 34 chaotic global rules) and the HECA local transition
 * (Eq. 5), confirmed by exact text extraction (pdftotext -layout) of the
 * paper's Table 3:
 *
 *   No.   1    2    3    4    5    6    7    8    9   10   11   12
 *   Rule  18  183   22  151   30   86  135  149   45   75   89  101
 *   No.  13   14   15   16   17   18   19   20   21   22   23   24
 *   Rule 60  102  153  195   90  165  105  106  120  169  225  129
 *   No.  25   26   27   28   29   30   31   32   33   34
 *   Rule 126 137  110  124  193  146  182  150  161  122
 *
 * L = 256 cells, split into two contiguous halves per Fig. 4 (single
 * periodic ring): cells 1..128 (0-indexed 0..127) run rule r1 ("ECA-r1",
 * green lattices), cells 129..256 (0-indexed 128..255) run rule r2
 * ("ECA-r2", blue lattices). Boundary condition is periodic (Eq. 5).
 */
public final class HecaRules {
    private HecaRules() {}

    /** LUT[Nr-1] = rule number for LUT index Nr (1..34). */
    public static final int[] LUT = {
        18, 183, 22, 151, 30, 86, 135, 149, 45, 75, 89, 101,
        60, 102, 153, 195, 90, 165, 105, 106, 120, 169, 225, 129,
        126, 137, 110, 124, 193, 146, 182, 150, 161, 122
    };

    public static final int L = 256;
    public static final int HALF = 128;

    /** Nr is 1-indexed (1..34), as produced by mod 34 + 1. */
    public static int ruleFor(int Nr) {
        return LUT[Nr - 1];
    }

    /** Standard ECA convention: output(pattern) = (rule >> pattern) & 1, pattern = left*4+center*2+right. */
    private static int ecaBit(int rule, int left, int center, int right) {
        int pattern = (left << 2) | (center << 1) | right;
        return (rule >> pattern) & 1;
    }

    /**
     * One synchronous HECA step (Eq. 5) over the full L=256-cell ring, periodic
     * boundary, first HALF cells governed by r1, remaining cells by r2.
     */
    public static boolean[] step(boolean[] S, int r1, int r2) {
        boolean[] next = new boolean[L];
        for (int i = 0; i < L; i++) {
            int left = S[(i - 1 + L) % L] ? 1 : 0;
            int center = S[i] ? 1 : 0;
            int right = S[(i + 1) % L] ? 1 : 0;
            int rule = (i < HALF) ? r1 : r2;
            next[i] = ecaBit(rule, left, center, right) == 1;
        }
        return next;
    }
}
