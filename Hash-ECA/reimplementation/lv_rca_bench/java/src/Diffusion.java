package lvrca;

/** Port of diffusion / dediffusion (encrypt.cpp / decrypt.cpp): per-block
 * chaos-ordered bit-plane RCA diffusion (see RcaDiffusion) plus block-to-block
 * XOR chaining on the transmitted ciphertext bytes (CBC-like). */
final class Diffusion {
    private Diffusion() {}

    private static int[] computeBlockOrderAndTimes(double[] chaosBlock9, int[] outTimes) {
        double[] chaosArray = new double[8];
        System.arraycopy(chaosBlock9, 0, chaosArray, 0, 8);
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 7 - i; j++) {
                if (chaosArray[j] > chaosArray[j + 1]) {
                    double td = chaosArray[j]; chaosArray[j] = chaosArray[j + 1]; chaosArray[j + 1] = td;
                    int ti = order[j]; order[j] = order[j + 1]; order[j + 1] = ti;
                }
            }
        }
        long raw = (long) (chaosBlock9[8] * 1.0e14);
        int mod8 = (int) (raw % 8);
        if (mod8 < 0) mod8 += 8;
        outTimes[0] = 10 + mod8;
        return order;
    }

    static void diffusion(byte[][] pixelBuf, double[][] chaosY, int m, int n) {
        int blocksPerRow = n / 8;
        int length = (m / 8) * blocksPerRow;
        byte[][][] blocks = RcaDiffusion.newBlocks();
        byte[][] preBlock = new byte[8][8];
        int[] timesHolder = new int[1];

        for (int id = 0; id < length; id++) {
            int idX = (id / blocksPerRow) * 8;
            int idY = (id % blocksPerRow) * 8;
            int[] order = computeBlockOrderAndTimes(chaosY[id], timesHolder);
            int times = timesHolder[0];

            RcaDiffusion.getBlocks(blocks, pixelBuf, idX, idY);
            RcaDiffusion.blockIterateEncrypt(blocks, order, times);
            RcaDiffusion.blockToPixels(blocks, pixelBuf, idX, idY);

            if (id > 0) {
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        int v = (pixelBuf[idX + row][idY + col] & 0xFF) ^ (preBlock[row][col] & 0xFF);
                        pixelBuf[idX + row][idY + col] = (byte) v;
                    }
                }
            }
            for (int row = 0; row < 8; row++) {
                System.arraycopy(pixelBuf[idX + row], idY, preBlock[row], 0, 8);
            }
        }
    }

    static void dediffusion(byte[][] pixelBuf, double[][] chaosY, int m, int n) {
        int blocksPerRow = n / 8;
        int length = (m / 8) * blocksPerRow;
        byte[][][] blocks = RcaDiffusion.newBlocks();
        byte[][] preBlock = new byte[8][8];
        byte[][] curBlock = new byte[8][8];
        int[] timesHolder = new int[1];

        for (int id = 0; id < length; id++) {
            int idX = (id / blocksPerRow) * 8;
            int idY = (id % blocksPerRow) * 8;
            int[] order = computeBlockOrderAndTimes(chaosY[id], timesHolder);
            int times = timesHolder[0];

            if (id == 0) {
                for (int row = 0; row < 8; row++) {
                    System.arraycopy(pixelBuf[idX + row], idY, preBlock[row], 0, 8);
                }
                RcaDiffusion.getBlocks(blocks, pixelBuf, idX, idY);
                RcaDiffusion.blockIterateDecrypt(blocks, order, times);
                RcaDiffusion.blockToPixels(blocks, pixelBuf, idX, idY);
            } else {
                for (int row = 0; row < 8; row++) {
                    System.arraycopy(pixelBuf[idX + row], idY, curBlock[row], 0, 8);
                }
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        int v = (pixelBuf[idX + row][idY + col] & 0xFF) ^ (preBlock[row][col] & 0xFF);
                        pixelBuf[idX + row][idY + col] = (byte) v;
                    }
                }
                for (int row = 0; row < 8; row++) {
                    System.arraycopy(curBlock[row], 0, preBlock[row], 0, 8);
                }
                RcaDiffusion.getBlocks(blocks, pixelBuf, idX, idY);
                RcaDiffusion.blockIterateDecrypt(blocks, order, times);
                RcaDiffusion.blockToPixels(blocks, pixelBuf, idX, idY);
            }
        }
    }
}
