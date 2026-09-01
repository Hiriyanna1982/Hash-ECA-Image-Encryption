package white;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class DiffusionV2 {

    static final int DEFAULT_ROUNDS = 4;
    // Final locked rule pool selected in Section 4.3 of the paper (intrinsic ECA analysis, Pool C).
    static final int[] RULES = {30,45,106,184};
    static final String DEFAULT_KEY_HEX = "3F9A7C2D8E4B1A6F9D0C3E7B5A2F8D1D";

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 4) {
            System.out.println("Usage: java white.DiffusionV2 <input> [output] [32HexKey] [Rd]");
            return;
        }

        String inputPath = args[0];
        String outputPath = args.length >= 2 ? args[1] : defaultOutputName(inputPath, "_encrypted.bmp");
        String keyHex = args.length >= 3 ? args[2] : DEFAULT_KEY_HEX;
        int Rd = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_ROUNDS;

        byte[] keyBytes = CipherUtil.keyBytesFromHex(keyHex);
        BufferedImage input = ImageIO.read(new File(inputPath));
        if (input == null) throw new IllegalArgumentException("Unreadable input image.");

        BufferedImage encrypted = encrypt(input, keyBytes, Rd);
        ImageIO.write(encrypted, "bmp", new File(outputPath));

        BufferedImage recovered = decrypt(encrypted, keyBytes, Rd);

        System.out.println("Diffusion complete.");
        System.out.println("Input : " + inputPath);
        System.out.println("Output: " + outputPath);
        System.out.println("Rd=" + Rd);
        System.out.println("Rule Set E={30,45,106,184}");
        System.out.println("Round-trip verified: " + compareImages(input, recovered));
    }

    public static BufferedImage encrypt(BufferedImage input, byte[] keyBytes, int rounds) throws Exception {
        return encrypt(input, keyBytes, rounds, RULES);
    }

    public static BufferedImage encrypt(BufferedImage input, byte[] keyBytes, int rounds, int[] rules) throws Exception {
        return encrypt(input, keyBytes, rounds, rules, true);
    }

    // useECA=false => C0 no-ECA baseline: MaskR/G/B = EseedR/G/B directly (ecaOneStep bypassed).
    // Position mixing, SHA-256 state chaining, alternating scan, cross-channel feedback all unchanged.
    public static BufferedImage encrypt(BufferedImage input, byte[] keyBytes, int rounds, int[] rules, boolean useECA) throws Exception {
        BufferedImage work = copyImage(input);
        byte[] master = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_DIFFUSION_INIT});

        for (int round = 1; round <= rounds; round++) {
            byte[] state = roundStartState(master, round);
            int feedback = state[2] & 0xff;
            boolean forward = (round % 2 == 1);

            if (forward) {
                for (int y = 0; y < work.getHeight(); y++) {
                    for (int x = 0; x < work.getWidth(); x++) {
                        int rgb = work.getRGB(x,y);
                        int R=(rgb>>16)&0xff, G=(rgb>>8)&0xff, B=rgb&0xff;
                        int[] m = masksFromState(state,x,y,rules,useECA);

                        int cR = CipherUtil.mod256(R + m[0] + feedback);
                        int cG = CipherUtil.mod256(G + m[1] + cR);
                        int cB = CipherUtil.mod256(B + m[2] + cG);

                        work.setRGB(x,y,(cR<<16)|(cG<<8)|cB);
                        state = nextState(state,x,y,round,R,G,B,cR,cG,cB);
                        feedback = cB;
                    }
                }
            } else {
                for (int y = work.getHeight()-1; y >= 0; y--) {
                    for (int x = work.getWidth()-1; x >= 0; x--) {
                        int rgb = work.getRGB(x,y);
                        int R=(rgb>>16)&0xff, G=(rgb>>8)&0xff, B=rgb&0xff;
                        int[] m = masksFromState(state,x,y,rules,useECA);

                        int cR = CipherUtil.mod256(R + m[0] + feedback);
                        int cG = CipherUtil.mod256(G + m[1] + cR);
                        int cB = CipherUtil.mod256(B + m[2] + cG);

                        work.setRGB(x,y,(cR<<16)|(cG<<8)|cB);
                        state = nextState(state,x,y,round,R,G,B,cR,cG,cB);
                        feedback = cB;
                    }
                }
            }
        }
        return work;
    }

    public static BufferedImage decrypt(BufferedImage input, byte[] keyBytes, int rounds) throws Exception {
        return decrypt(input, keyBytes, rounds, RULES);
    }

    public static BufferedImage decrypt(BufferedImage input, byte[] keyBytes, int rounds, int[] rules) throws Exception {
        return decrypt(input, keyBytes, rounds, rules, true);
    }

    public static BufferedImage decrypt(BufferedImage input, byte[] keyBytes, int rounds, int[] rules, boolean useECA) throws Exception {
        BufferedImage work = copyImage(input);
        byte[] master = CipherUtil.sha256(keyBytes, new byte[]{CipherUtil.TAG_DIFFUSION_INIT});

        for (int round = rounds; round >= 1; round--) {
            byte[] state = roundStartState(master, round);
            int feedback = state[2] & 0xff;
            BufferedImage recoveredRound = new BufferedImage(work.getWidth(), work.getHeight(), BufferedImage.TYPE_INT_RGB);
            boolean forward = (round % 2 == 1);

            if (forward) {
                for (int y = 0; y < work.getHeight(); y++) {
                    for (int x = 0; x < work.getWidth(); x++) {
                        int rgb = work.getRGB(x,y);
                        int cR=(rgb>>16)&0xff, cG=(rgb>>8)&0xff, cB=rgb&0xff;
                        int[] m = masksFromState(state,x,y,rules,useECA);

                        int R = CipherUtil.mod256(cR - m[0] - feedback);
                        int G = CipherUtil.mod256(cG - m[1] - cR);
                        int B = CipherUtil.mod256(cB - m[2] - cG);

                        recoveredRound.setRGB(x,y,(R<<16)|(G<<8)|B);
                        state = nextState(state,x,y,round,R,G,B,cR,cG,cB);
                        feedback = cB;
                    }
                }
            } else {
                for (int y = work.getHeight()-1; y >= 0; y--) {
                    for (int x = work.getWidth()-1; x >= 0; x--) {
                        int rgb = work.getRGB(x,y);
                        int cR=(rgb>>16)&0xff, cG=(rgb>>8)&0xff, cB=rgb&0xff;
                        int[] m = masksFromState(state,x,y,rules,useECA);

                        int R = CipherUtil.mod256(cR - m[0] - feedback);
                        int G = CipherUtil.mod256(cG - m[1] - cR);
                        int B = CipherUtil.mod256(cB - m[2] - cG);

                        recoveredRound.setRGB(x,y,(R<<16)|(G<<8)|B);
                        state = nextState(state,x,y,round,R,G,B,cR,cG,cB);
                        feedback = cB;
                    }
                }
            }
            work = recoveredRound;
        }
        return work;
    }

    static byte[] roundStartState(byte[] master, int round) throws Exception {
        return CipherUtil.sha256(master, new byte[]{CipherUtil.TAG_DIFFUSION_ROUND}, CipherUtil.be32(round));
    }

    static int[] masksFromState(byte[] state, int x, int y, int[] rules, boolean useECA) {
        int posMix = ((x*x + 3*x*y + y*y) ^ (x+y)) & 0xff;

        int sR = (posMix ^ (state[0] & 0xff)) & 0xff;
        int sG = (posMix ^ (state[1] & 0xff)) & 0xff;
        int sB = (posMix ^ (state[2] & 0xff)) & 0xff;

        if (!useECA) {
            // C0 baseline: mask = Eseed directly, no cellular-automaton step
            return new int[]{ sR, sG, sB };
        }

        int ruleR = rules[(state[0] & 0xff) % rules.length];
        int ruleG = rules[(state[1] & 0xff) % rules.length];
        int ruleB = rules[(state[2] & 0xff) % rules.length];

        return new int[]{
            ecaOneStep(sR, ruleR),
            ecaOneStep(sG, ruleG),
            ecaOneStep(sB, ruleB)
        };
    }

    static byte[] nextState(byte[] state, int x, int y, int round,
                            int R, int G, int B, int cR, int cG, int cB) throws Exception {
        return CipherUtil.sha256(
            state,
            CipherUtil.be32(x),
            CipherUtil.be32(y),
            CipherUtil.be32(round),
            new byte[]{CipherUtil.TAG_DIFFUSION_PIXEL},
            new byte[]{(byte)R,(byte)G,(byte)B,(byte)cR,(byte)cG,(byte)cB}
        );
    }

    public static int ecaOneStep(int seed, int rule) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            int left = (seed >> ((i+1)%8)) & 1;
            int center = (seed >> i) & 1;
            int right = (seed >> ((i+7)%8)) & 1;
            int neighborhood = (left<<2) | (center<<1) | right;
            int nextBit = (rule >> neighborhood) & 1;
            result |= nextBit << i;
        }
        return result & 0xff;
    }

    static BufferedImage copyImage(BufferedImage input) {
        int W=input.getWidth(), H=input.getHeight();
        BufferedImage copy = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<H;y++)
            for (int x=0;x<W;x++)
                copy.setRGB(x,y,input.getRGB(x,y)&0xffffff);
        return copy;
    }

    static boolean compareImages(BufferedImage a, BufferedImage b) {
        if (a.getWidth()!=b.getWidth() || a.getHeight()!=b.getHeight()) return false;
        for (int y=0;y<a.getHeight();y++)
            for (int x=0;x<a.getWidth();x++)
                if ((a.getRGB(x,y)&0xffffff)!=(b.getRGB(x,y)&0xffffff)) return false;
        return true;
    }

    static String defaultOutputName(String inputPath, String suffix) {
        File f = new File(inputPath);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0,dot) : name;
        return new File(f.getAbsoluteFile().getParentFile(), base+suffix).getPath();
    }
}
