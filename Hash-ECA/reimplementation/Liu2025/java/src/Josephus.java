package liu;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-ring Josephus scrambling (paper Section III-A), applied concretely
 * as: image regarded as m rings (rows), each of n elements (columns) -
 * this always divides evenly here, so the paper's uneven-remainder-ring
 * branch never triggers in the encryption application.
 *
 * A4 (documented, non-authors'-verbatim convention - see write-up):
 * the paper's own worked example (Fig. 7) could not be reproduced under
 * any single consistent counting rule we tested (best match: 7/14
 * eliminations). We fix one clean, fully self-consistent rule:
 *   - active-ring list shrinks as rows empty out.
 *   - inter-ring: newRingIdx = (curRingIdx + a_i) mod (#active rings),
 *     curRingIdx initialized to (#rings - 1) before step 1.
 *     (This part IS verified against the paper's own example: it exactly
 *     reproduces all 3 ring selections we could check - E2, E3, E2.)
 *   - intra-ring: newPos = (pointer + b_i) mod (ring size), pointer
 *     persists at its post-removal array index and defaults to 0 on a
 *     ring's first visit.
 *
 * Produces perm[] such that perm[i] = original flat index (row*n+col) of
 * the i-th eliminated element. Scrambling: S[i] = P_flat[perm[i]].
 * Descrambling: P_flat[perm[i]] = S[i]. Because perm[] depends only on
 * (m, n, A, B) - not on pixel values - both directions call this once.
 */
public final class Josephus {

    public static int[] permutation(int m, int n, int[] A, int[] B) {
        int mn = m * n;
        if (A.length < mn || B.length < mn) {
            throw new IllegalArgumentException("A/B sequences too short");
        }

        List<List<Integer>> ringContents = new ArrayList<>(m);
        for (int r = 0; r < m; r++) {
            List<Integer> cols = new ArrayList<>(n);
            for (int c = 0; c < n; c++) cols.add(c);
            ringContents.add(cols);
        }
        List<Integer> activeRings = new ArrayList<>(m);
        for (int r = 0; r < m; r++) activeRings.add(r);
        int[] ringPointer = new int[m];

        int curRingIdx = activeRings.size() - 1;
        int[] perm = new int[mn];

        for (int i = 0; i < mn; i++) {
            int a = A[i];
            int b = B[i];
            int N = activeRings.size();
            int newRingIdx = Math.floorMod(curRingIdx + a, N);
            int row = activeRings.get(newRingIdx);
            List<Integer> ring = ringContents.get(row);
            int k = ring.size();
            int ref = ringPointer[row];
            int newPos = Math.floorMod(ref + b, k);
            int col = ring.remove(newPos);
            perm[i] = row * n + col;

            if (ring.isEmpty()) {
                activeRings.remove(newRingIdx);
                if (!activeRings.isEmpty()) {
                    curRingIdx = newRingIdx % activeRings.size();
                }
            } else {
                ringPointer[row] = newPos % ring.size();
                curRingIdx = newRingIdx;
            }
        }
        return perm;
    }

    public static byte[] scramble(byte[] planeRowMajor, int[] perm) {
        int mn = planeRowMajor.length;
        byte[] out = new byte[mn];
        for (int i = 0; i < mn; i++) out[i] = planeRowMajor[perm[i]];
        return out;
    }

    public static byte[] descramble(byte[] scrambledRowMajor, int[] perm) {
        int mn = scrambledRowMajor.length;
        byte[] out = new byte[mn];
        for (int i = 0; i < mn; i++) out[perm[i]] = scrambledRowMajor[i];
        return out;
    }
}
