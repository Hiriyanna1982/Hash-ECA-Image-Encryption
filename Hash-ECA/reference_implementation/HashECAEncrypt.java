package white;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class HashECAEncrypt {

    static final int B = 8;
    static final int RS = 1;
    static final int RD = 4;
    static final String DEFAULT_KEY_HEX = "3F9A7C2D8E4B1A6F9D0C3E7B5A2F8D1D";

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: java white.HashECAEncrypt <inputImage> [32HexKey]");
            return;
        }

        String inputPath = args[0];
        String keyHex = args.length == 2 ? args[1] : DEFAULT_KEY_HEX;
        byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);

        BufferedImage original = ImageIO.read(new File(inputPath));
        if (original == null) throw new IllegalArgumentException("Unreadable input image.");

        if (original.getWidth() % B != 0 || original.getHeight() % B != 0)
            throw new IllegalArgumentException("Image dimensions must be divisible by B=8.");

        String scrambledPath = outputName(inputPath, "_scrambled.bmp");
        String encryptedPath = outputName(inputPath, "_encrypted.bmp");
        String recoveredPath = outputName(inputPath, "_recovered.bmp");

        BufferedImage scrambled = ScrambleV2.scramble(original, keyBytes, B, RS);
        BufferedImage encrypted = DiffusionV2.encrypt(scrambled, keyBytes, RD);
        BufferedImage recoveredScrambled = DiffusionV2.decrypt(encrypted, keyBytes, RD);
        BufferedImage recoveredOriginal = ScrambleV2.unscramble(recoveredScrambled, keyBytes, B, RS);

        ImageIO.write(scrambled, "bmp", new File(scrambledPath));
        ImageIO.write(encrypted, "bmp", new File(encryptedPath));
        ImageIO.write(recoveredOriginal, "bmp", new File(recoveredPath));

        System.out.println("Full test complete.");
        System.out.println("Input      : " + inputPath);
        System.out.println("Scrambled  : " + scrambledPath);
        System.out.println("Encrypted  : " + encryptedPath);
        System.out.println("Recovered  : " + recoveredPath);
        System.out.println("B=8, Rs=1, Rd=4");
        System.out.println("Exact recovery: " + DiffusionV2.compareImages(original, recoveredOriginal));
    }

    static String outputName(String inputPath, String suffix) {
        File f = new File(inputPath);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0,dot) : name;
        return new File(f.getAbsoluteFile().getParentFile(), base+suffix).getPath();
    }
}
