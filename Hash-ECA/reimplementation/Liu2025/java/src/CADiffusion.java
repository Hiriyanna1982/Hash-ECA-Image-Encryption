package liu;

/**
 * Two-dimensional cellular-automata diffusion (paper Eqs. 4-13, Fig. 8).
 *
 * Padding note (structural, not a numbered assumption - it is the only
 * reading that makes the stated index ranges i=2..m+1, j=2..n+1 (needing
 * both j-1 and j+1, i.e. up to n+2) well-formed): all of S', W', R' are
 * conceptually padded to (m+1) x (n+2): a zero row on top (row 1, never
 * actually read), a left pad column (col 1) and a right pad column
 * (col n+2). The left pad column of the *new* (t+1) array is dynamic:
 * fixed to 5 for the special first real row (Eq. 7), and equal to the
 * previous row's last real column value for every subsequent row
 * ("S'^{t+1}_{i,1} = S'^{t+1}_{i-1,n+1}"). The right pad column is never
 * written and stays 0 throughout (the paper gives no update rule for it).
 * W' and R' never need neighbor offsets applied to themselves, so they are
 * plain m x n arrays indexed directly.
 *
 * We keep two matrices: oldS (the pre-diffusion scrambled image S', static,
 * zero-padded) and newS (the post-diffusion accumulator, t+1), because
 * several rules reference the OLD value of the same row's left neighbor
 * (S'^t_{i,j-1}), which would already be overwritten if updated in place.
 */
public final class CADiffusion {

    /** Forward diffusion. scrambled/W/R are m x n row-major (0-indexed). Returns ciphertext m x n row-major. */
    public static byte[] diffuse(byte[] scrambled, int[] W, int[] R, int m, int n) {
        int[][] oldS = new int[m + 2][n + 3]; // 1-indexed rows 1..m+1, cols 1..n+2
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                oldS[r + 2][c + 2] = scrambled[r * n + c] & 0xFF;
            }
        }
        // row 1, col 1, col n+2 of oldS stay 0 (static padding).

        int[][] newS = new int[m + 2][n + 3];

        for (int i = 2; i <= m + 1; i++) {
            int realRow = i - 2;
            if (i == 2) {
                newS[i][1] = 5;
                for (int j = 2; j <= n + 1; j++) {
                    int realCol = j - 2;
                    int r = R[realRow * n + realCol];
                    newS[i][j] = (newS[i][j - 1] ^ oldS[i][j] ^ r) & 0xFF;
                }
            } else {
                newS[i][1] = newS[i - 1][n + 1]; // carry: previous row's LAST REAL column (Eq. 13), not the (always-0) right pad
                for (int j = 2; j <= n + 1; j++) {
                    int realCol = j - 2;
                    int w = W[realRow * n + realCol];
                    int r = R[realRow * n + realCol];
                    int val = applyRule(w, oldS, newS, i, j, r, true);
                    newS[i][j] = val & 0xFF;
                }
            }
            // newS[i][n+2] (right pad) stays 0.
        }

        byte[] out = new byte[m * n];
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                out[r * n + c] = (byte) newS[r + 2][c + 2];
            }
        }
        return out;
    }

    /** Inverse diffusion. cipher/W/R are m x n row-major. Returns the recovered scrambled matrix S'. */
    public static byte[] undiffuse(byte[] cipher, int[] W, int[] R, int m, int n) {
        int[][] newS = new int[m + 2][n + 3];
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                newS[r + 2][c + 2] = cipher[r * n + c] & 0xFF;
            }
        }
        // Reconstruct the deterministic left-pad column for every row from the ciphertext itself.
        for (int i = 2; i <= m + 1; i++) {
            if (i == 2) {
                newS[i][1] = 5;
            } else {
                newS[i][1] = newS[i - 1][n + 1]; // previous row's last real ciphertext column (Eq. 13); already known since newS is the full ciphertext
            }
        }

        int[][] oldS = new int[m + 2][n + 3];

        for (int i = 2; i <= m + 1; i++) {
            int realRow = i - 2;
            if (i == 2) {
                for (int j = 2; j <= n + 1; j++) {
                    int realCol = j - 2;
                    int r = R[realRow * n + realCol];
                    oldS[i][j] = (newS[i][j] ^ newS[i][j - 1] ^ r) & 0xFF;
                }
            } else {
                for (int j = 2; j <= n + 1; j++) {
                    int realCol = j - 2;
                    int w = W[realRow * n + realCol];
                    int r = R[realRow * n + realCol];
                    int val = applyRule(w, oldS, newS, i, j, r, false);
                    oldS[i][j] = val & 0xFF;
                }
            }
        }

        byte[] out = new byte[m * n];
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                out[r * n + c] = (byte) oldS[r + 2][c + 2];
            }
        }
        return out;
    }

    /**
     * forward=true: compute newS[i][j] from oldS (and already-known newS neighbors).
     * forward=false: compute oldS[i][j] from newS (fully known) and already-recovered
     *                oldS[i][j-1] (same row, left, recovered earlier in this same pass).
     * XOR is its own inverse, so solving each rule for the unknown just XORs the
     * ciphertext-side value back in instead of the plaintext-side one.
     */
    private static int applyRule(int w, int[][] oldS, int[][] newS, int i, int j, int r, boolean forward) {
        int nUL = newS[i - 1][j - 1];
        int nU  = newS[i - 1][j];
        int nUR = newS[i - 1][j + 1];
        if (forward) {
            int cur = oldS[i][j];
            int left = oldS[i][j - 1];
            switch (w) {
                case 1: return nUL ^ nU ^ cur ^ r;
                case 2: return nUL ^ nUR ^ cur ^ r;
                case 3: return nUL ^ left ^ cur ^ r;
                case 4: return nU ^ left ^ cur ^ r;
                case 5: return nU ^ nUR ^ cur ^ r;
                case 6: return nUR ^ left ^ cur ^ r;
                default: throw new IllegalArgumentException("W' out of range: " + w);
            }
        } else {
            int cipherVal = newS[i][j];
            int left = oldS[i][j - 1]; // already recovered (left-to-right pass)
            switch (w) {
                case 1: return cipherVal ^ nUL ^ nU ^ r;
                case 2: return cipherVal ^ nUL ^ nUR ^ r;
                case 3: return cipherVal ^ nUL ^ left ^ r;
                case 4: return cipherVal ^ nU ^ left ^ r;
                case 5: return cipherVal ^ nU ^ nUR ^ r;
                case 6: return cipherVal ^ nUR ^ left ^ r;
                default: throw new IllegalArgumentException("W' out of range: " + w);
            }
        }
    }
}
