package lvrca;

/** Port of pretreatment / depretreatment (encrypt.cpp / decrypt.cpp). Adds/removes
 * 4 chaos-positioned "disturbing pixel" deltas per 8x8 block. */
final class Pretreatment {
    private Pretreatment() {}

    static void pretreatment(byte[][] pixelBuf, double[][] chaosX, int m, int n) {
        int blocksPerRow = n / 8;
        int length = (m / 8) * blocksPerRow;
        for (int id = 0; id < length; id++) {
            int idX = (id / blocksPerRow) * 8;
            int idY = (id % blocksPerRow) * 8;
            int[] randPos = new int[4];
            int[] randPix = new int[4];
            for (int i = 0; i < 4; i++) {
                randPos[i] = (int) (((long) (chaosX[id][1 + i] * 1.0e14)) % 64);
                if (randPos[i] < 0) randPos[i] += 64;
                randPix[i] = (int) (((long) (chaosX[id][5 + i] * 1.0e14)) % 256);
                if (randPix[i] < 0) randPix[i] += 256;
            }
            for (int i = 0; i < 4; i++) {
                int x = randPos[i] / 8;
                int y = randPos[i] % 8;
                int cur = pixelBuf[idX + x][idY + y] & 0xFF;
                pixelBuf[idX + x][idY + y] = (byte) ((cur + randPix[i]) & 0xFF);
            }
        }
    }

    static void depretreatment(byte[][] pixelBuf, double[][] chaosX, int m, int n) {
        int blocksPerRow = n / 8;
        int length = (m / 8) * blocksPerRow;
        for (int id = 0; id < length; id++) {
            int idX = (id / blocksPerRow) * 8;
            int idY = (id % blocksPerRow) * 8;
            int[] randPos = new int[4];
            int[] randPix = new int[4];
            for (int i = 0; i < 4; i++) {
                randPos[i] = (int) (((long) (chaosX[id][1 + i] * 1.0e14)) % 64);
                if (randPos[i] < 0) randPos[i] += 64;
                randPix[i] = (int) (((long) (chaosX[id][5 + i] * 1.0e14)) % 256);
                if (randPix[i] < 0) randPix[i] += 256;
            }
            for (int i = 0; i < 4; i++) {
                int x = randPos[i] / 8;
                int y = randPos[i] % 8;
                int cur = pixelBuf[idX + x][idY + y] & 0xFF;
                pixelBuf[idX + x][idY + y] = (byte) (((cur - randPix[i]) % 256 + 256) % 256);
            }
        }
    }
}
