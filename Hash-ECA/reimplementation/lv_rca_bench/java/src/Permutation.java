package lvrca;

/** Port of permutate / depermutate and block_shuffle / block_deshuffle
 * (encrypt.cpp / decrypt.cpp): whole-image block-level permutation, blocks
 * sorted ascending by each block's own chaos value with the last block's
 * key forced to 0 (the authors' mechanism to make the last plaintext block
 * tend to land first, per the paper's stated diffusion-efficiency goal). */
final class Permutation {
    private Permutation() {}

    private static int[] computeOrder(double[][] chaosX, int length) {
        double[] chaosArray = new double[length];
        int[] order = new int[length];
        for (int i = 0; i < length; i++) {
            chaosArray[i] = chaosX[i][0];
            order[i] = i;
        }
        chaosArray[length - 1] = 0;
        // Exact bubble sort (matches the reference code's tie-breaking behavior).
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length - 1 - i; j++) {
                if (chaosArray[j] > chaosArray[j + 1]) {
                    double td = chaosArray[j]; chaosArray[j] = chaosArray[j + 1]; chaosArray[j + 1] = td;
                    int ti = order[j]; order[j] = order[j + 1]; order[j + 1] = ti;
                }
            }
        }
        return order;
    }

    static void permutate(byte[][] pixelBuf, double[][] chaosX, int m, int n) {
        int length = (m / 8) * (n / 8);
        int[] order = computeOrder(chaosX, length);
        blockShuffle(pixelBuf, order, length, m, n);
    }

    static void depermutate(byte[][] pixelBuf, double[][] chaosX, int m, int n) {
        int length = (m / 8) * (n / 8);
        int[] order = computeOrder(chaosX, length);
        blockDeshuffle(pixelBuf, order, length, m, n);
    }

    private static void blockShuffle(byte[][] pixelBuf, int[] order, int length, int m, int n) {
        byte[][] temp = new byte[m][n];
        int blocksPerRow = n / 8;
        for (int i = 0; i < length; i++) {
            int readX = (order[i] / blocksPerRow) * 8;
            int readY = (order[i] % blocksPerRow) * 8;
            int writeX = (i / blocksPerRow) * 8;
            int writeY = (i % blocksPerRow) * 8;
            for (int row = 0; row < 8; row++) {
                System.arraycopy(pixelBuf[readX + row], readY, temp[writeX + row], writeY, 8);
            }
        }
        for (int i = 0; i < m; i++) System.arraycopy(temp[i], 0, pixelBuf[i], 0, n);
    }

    private static void blockDeshuffle(byte[][] pixelBuf, int[] order, int length, int m, int n) {
        byte[][] temp = new byte[m][n];
        int blocksPerRow = n / 8;
        for (int i = 0; i < length; i++) {
            int readX = (i / blocksPerRow) * 8;
            int readY = (i % blocksPerRow) * 8;
            int writeX = (order[i] / blocksPerRow) * 8;
            int writeY = (order[i] % blocksPerRow) * 8;
            for (int row = 0; row < 8; row++) {
                System.arraycopy(pixelBuf[readX + row], readY, temp[writeX + row], writeY, 8);
            }
        }
        for (int i = 0; i < m; i++) System.arraycopy(temp[i], 0, pixelBuf[i], 0, n);
    }
}
