package lvrca;

/**
 * Port of the bit-plane reversible-CA diffusion core (RCA_func, get_blocks,
 * block_to_pixels, block_iterate) from encrypt.cpp / decrypt.cpp. This is
 * the Life-liked-CA-with-balanced-rule step: each 8x8 pixel block is split
 * into 8 single-bit planes; each round applies a Moore-neighborhood CA step
 * (periodic boundary) to 7 of the 8 planes, but only a parity checksum of
 * that step survives into the 8th plane -- the other 7 planes are simply
 * rotated forward by one position in a chaos-derived shuffle order. This is
 * what makes the construction reversible: decrypt runs the exact mirror
 * rotation/checksum in the opposite direction.
 */
final class RcaDiffusion {
    private RcaDiffusion() {}

    /** blocks[plane][row][col], plane 0..7 (plane 0 = LSB, plane 7 = MSB), each 8x8, values 0/1. */
    static void getBlocks(byte[][][] blocks, byte[][] pixelBuf, int idX, int idY) {
        for (int row = idX; row < idX + 8; row++) {
            for (int col = idY; col < idY + 8; col++) {
                int val = pixelBuf[row][col] & 0xFF;
                for (int i = 0; i < 8; i++) {
                    blocks[i][row - idX][col - idY] = (byte) ((val >> i) & 1);
                }
            }
        }
    }

    static void blockToPixels(byte[][][] blocks, byte[][] pixelBuf, int idX, int idY) {
        for (int row = idX; row < idX + 8; row++) {
            for (int col = idY; col < idY + 8; col++) {
                int val = 0;
                for (int i = 7; i >= 0; i--) {
                    val = (val << 1) + blocks[i][row - idX][col - idY];
                }
                pixelBuf[row][col] = (byte) val;
            }
        }
    }

    /** In-place Life-liked CA step on a single 8x8 bit-plane, periodic (toroidal) boundary. */
    private static void rcaFunc(byte[][] block) {
        byte[][] newBlock = new byte[8][8]; // zero-initialized, matches C++'s memset(...,0,...)
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int up = (i == 0) ? 7 : i - 1;
                int down = (i == 7) ? 0 : i + 1;
                int left = (j == 0) ? 7 : j - 1;
                int right = (j == 7) ? 0 : j + 1;
                int cnt = block[up][left] + block[up][j] + block[up][right]
                        + block[i][left] + block[i][right]
                        + block[down][left] + block[down][j] + block[down][right];
                boolean oddCount = (cnt == 7 || cnt == 5 || cnt == 3 || cnt == 1);
                if (block[i][j] == 0) {
                    if (oddCount) newBlock[i][j] = 1;
                }
                // NOTE: preserved exactly as the reference code has it. When
                // block[i][j]==1, the only explicit action is
                // "if oddCount, set 0" -- which is already the array's
                // zero-initialized default, so this branch is a no-op and
                // newBlock[i][j] stays 0 regardless of cnt. This looks like
                // it destroys the "1" state unconditionally, but by design
                // it doesn't matter: block_iterate below only keeps this
                // function's output as an input to a parity checksum over 7
                // planes, never as the persisted plane value directly (the
                // persisted planes are rotated copies of the PRE-iteration
                // state). We do not "correct" this per the standing
                // instruction to preserve the authors' C++ quirks exactly.
                if (block[i][j] == 1) {
                    if (oddCount) newBlock[i][j] = 0;
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            System.arraycopy(newBlock[i], 0, block[i], 0, 8);
        }
    }

    private static byte[][][] deepCopy(byte[][][] blocks) {
        byte[][][] copy = new byte[8][8][8];
        for (int p = 0; p < 8; p++) {
            for (int r = 0; r < 8; r++) {
                System.arraycopy(blocks[p][r], 0, copy[p][r], 0, 8);
            }
        }
        return copy;
    }

    /** Encrypt-side block_iterate: RCA-update planes order[1..7], checksum into order[7], rotate order[0..6] <- temp[order[1..7]]. */
    static void blockIterateEncrypt(byte[][][] blocks, int[] order, int times) {
        for (int t = 0; t < times; t++) {
            byte[][][] temp = deepCopy(blocks);

            for (int i = 1; i < 8; i++) {
                rcaFunc(blocks[order[i]]);
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int sum = 0;
                    for (int i = 0; i < 8; i++) sum += blocks[order[i]][row][col];
                    blocks[order[7]][row][col] = (byte) (sum % 2);
                }
            }

            for (int id = 0; id < 7; id++) {
                for (int row = 0; row < 8; row++) {
                    System.arraycopy(temp[order[id + 1]][row], 0, blocks[order[id]][row], 0, 8);
                }
            }
        }
    }

    /** Decrypt-side block_iterate: RCA-update planes order[0..6], checksum into order[0], rotate order[1..7] <- temp[order[0..6]]. */
    static void blockIterateDecrypt(byte[][][] blocks, int[] order, int times) {
        for (int t = 0; t < times; t++) {
            byte[][][] temp = deepCopy(blocks);

            for (int i = 0; i < 7; i++) {
                rcaFunc(blocks[order[i]]);
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int sum = 0;
                    for (int i = 0; i < 8; i++) sum += blocks[order[i]][row][col];
                    blocks[order[0]][row][col] = (byte) (sum % 2);
                }
            }

            for (int id = 1; id < 8; id++) {
                for (int row = 0; row < 8; row++) {
                    System.arraycopy(temp[order[id - 1]][row], 0, blocks[order[id]][row], 0, 8);
                }
            }
        }
    }

    static byte[][][] newBlocks() {
        return new byte[8][8][8];
    }
}
