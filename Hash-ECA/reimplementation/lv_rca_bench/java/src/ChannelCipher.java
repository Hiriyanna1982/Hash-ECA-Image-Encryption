package lvrca;

/** Port of image::encrypt / image::decrypt (image.cpp): the 2-round
 * pretreatment -> permutate -> diffusion pipeline for a single 8-bit
 * (grayscale) channel. Operates on a byte[m][n] pixel buffer in place. */
public final class ChannelCipher {
    private ChannelCipher() {}

    public static void encrypt(byte[][] pixelBuf, int m, int n, String key) {
        int length = (m / 8) * (n / 8);
        for (int round = 0; round < 2; round++) {
            double[][] chaos = new double[length][9];
            KeyChaos.randNumCreate(chaos, length, key, round);
            Pretreatment.pretreatment(pixelBuf, chaos, m, n);
            Permutation.permutate(pixelBuf, chaos, m, n);
            Diffusion.diffusion(pixelBuf, chaos, m, n);
        }
    }

    public static void decrypt(byte[][] pixelBuf, int m, int n, String key) {
        int length = (m / 8) * (n / 8);
        for (int round = 0; round < 2; round++) {
            double[][] chaos = new double[length][9];
            KeyChaos.derandNumCreate(chaos, length, key, round);
            Diffusion.dediffusion(pixelBuf, chaos, m, n);
            Permutation.depermutate(pixelBuf, chaos, m, n);
            Pretreatment.depretreatment(pixelBuf, chaos, m, n);
        }
    }
}
