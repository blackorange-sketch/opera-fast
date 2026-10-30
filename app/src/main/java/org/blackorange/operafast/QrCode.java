package org.blackorange.operafast;

/** Minimal QR Code generator: byte mode, error correction M, versions 1-13. */
final class QrCode {
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9};

    final int size;
    private final boolean[][] mod;
    private final boolean[][] fn;

    /** Returns null if the text doesn't fit. */
    static QrCode encode(String text) {
        return encode(text, -1);
    }

    static QrCode encode(String text, int forceMask) {
        byte[] data;
        try {
            data = text.getBytes("UTF-8");
        } catch (Exception e) {
            return null;
        }
        for (int ver = 1; ver <= 13; ver++) {
            int cap = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver];
            int countBits = ver <= 9 ? 8 : 16;
            if (4 + countBits + data.length * 8 <= cap * 8) {
                return new QrCode(ver, codewords(data, ver, cap, countBits), forceMask);
            }
        }
        return null;
    }

    boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && mod[y][x];
    }

    private static byte[] codewords(byte[] data, int ver, int cap, int countBits) {
        java.util.ArrayList<Integer> bits = new java.util.ArrayList<Integer>();
        put(bits, 4, 4);
        put(bits, data.length, countBits);
        for (byte b : data) put(bits, b & 0xFF, 8);
        int total = cap * 8;
        for (int i = 0; i < 4 && bits.size() < total; i++) bits.add(0);
        while (bits.size() % 8 != 0) bits.add(0);
        byte[] out = new byte[cap];
        for (int i = 0; i < bits.size(); i++) out[i >> 3] |= bits.get(i) << (7 - (i & 7));
        int used = bits.size() / 8;
        for (int i = used, pad = 0xEC; i < cap; i++, pad ^= 0xEC ^ 0x11) out[i] = (byte) pad;
        return interleave(out, ver);
    }

    private static void put(java.util.ArrayList<Integer> bits, int val, int len) {
        for (int i = len - 1; i >= 0; i--) bits.add((val >>> i) & 1);
    }

    private QrCode(int ver, byte[] cw, int forceMask) {
        size = ver * 4 + 17;
        mod = new boolean[size][size];
        fn = new boolean[size][size];
        drawFunctionPatterns(ver);
        drawCodewords(cw);
        int best = 0;
        if (forceMask >= 0) {
            best = forceMask;
        } else {
            int bestPen = Integer.MAX_VALUE;
            for (int m = 0; m < 8; m++) {
                applyMask(m);
                drawFormat(m);
                int p = penalty();
                if (p < bestPen) {
                    bestPen = p;
                    best = m;
                }
                applyMask(m);   // undo (xor again)
            }
        }
        applyMask(best);
        drawFormat(best);
    }

    private int penalty() {
        int pen = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = pass == 0 ? mod[a][b] : mod[b][a];
                    boolean prev = pass == 0 ? mod[a][b - 1] : mod[b - 1][a];
                    if (cur == prev) {
                        run++;
                        if (run == 5) pen += 3;
                        else if (run > 5) pen++;
                    } else {
                        run = 1;
                    }
                }
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = mod[y][x];
                if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) pen += 3;
            }
        }
        int dark = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (mod[y][x]) dark++;
        int k = Math.abs(dark * 20 - size * size * 10) / (size * size);
        return pen + k * 10;
    }

    private void drawFunctionPatterns(int ver) {
        for (int i = 0; i < size; i++) {
            setFn(6, i, i % 2 == 0);
            setFn(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] pos = alignPos(ver);
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        setFn(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormat(0);
        if (ver >= 7) {
            int rem = ver;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = ver << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean b = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3, c = i / 3;
                setFn(a, c, b);
                setFn(c, a, b);
            }
        }
    }

    private void finder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) setFn(xx, yy, d != 2 && d != 4);
            }
        }
    }

    private void setFn(int x, int y, boolean v) {
        mod[y][x] = v;
        fn[y][x] = true;
    }

    private void drawFormat(int mask) {
        int data = (0 << 3) | mask;   // error correction M = 0b00
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFn(8, i, bit(bits, i));
        setFn(8, 7, bit(bits, 6));
        setFn(8, 8, bit(bits, 7));
        setFn(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFn(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFn(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFn(8, size - 15 + i, bit(bits, i));
        setFn(8, size - 8, true);
    }

    private static boolean bit(int v, int i) {
        return ((v >>> i) & 1) != 0;
    }

    private int[] alignPos(int ver) {
        if (ver == 1) return new int[0];
        int n = ver / 7 + 2;
        int step = (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, p = size - 7; i >= 1; i--, p -= step) r[i] = p;
        return r;
    }

    private static int rawModules(int ver) {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int n = ver / 7 + 2;
            r -= (25 * n - 10) * n - 55;
            if (ver >= 7) r -= 36;
        }
        return r;
    }

    private static byte[] interleave(byte[] data, int ver) {
        int nb = NUM_BLOCKS[ver], eccLen = ECC_PER_BLOCK[ver];
        int raw = rawModules(ver) / 8;
        int shortCount = nb - raw % nb;
        int shortLen = raw / nb;
        byte[][] blocks = new byte[nb][];
        byte[] div = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < nb; i++) {
            int dl = shortLen - eccLen + (i < shortCount ? 0 : 1);
            byte[] dat = java.util.Arrays.copyOfRange(data, k, k + dl);
            k += dl;
            byte[] block = java.util.Arrays.copyOf(dat, shortLen + 1);
            byte[] ecc = rsRemainder(dat, div);
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            blocks[i] = block;
        }
        byte[] out = new byte[raw];
        int k = 0;
        for (int i = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < nb; j++) {
                if (i != shortLen - eccLen || j >= shortCount) out[k++] = blocks[j][i];
            }
        }
        return out;
    }

    private static byte[] rsDivisor(int d) {
        byte[] r = new byte[d];
        r[d - 1] = 1;
        int root = 1;
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                r[j] = (byte) mul(r[j] & 0xFF, root);
                if (j + 1 < d) r[j] ^= r[j + 1];
            }
            root = mul(root, 2);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] div) {
        byte[] r = new byte[div.length];
        for (byte b : data) {
            int f = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= mul(div[i] & 0xFF, f);
        }
        return r;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void drawCodewords(byte[] cw) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean up = ((right + 1) & 2) == 0;
                    int y = up ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < cw.length * 8) {
                        mod[y][x] = bit(cw[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int m) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean inv;
                switch (m) {
                    case 0: inv = (x + y) % 2 == 0; break;
                    case 1: inv = y % 2 == 0; break;
                    case 2: inv = x % 3 == 0; break;
                    case 3: inv = (x + y) % 3 == 0; break;
                    case 4: inv = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: inv = x * y % 2 + x * y % 3 == 0; break;
                    case 6: inv = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: inv = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (inv && !fn[y][x]) mod[y][x] = !mod[y][x];
            }
        }
    }
}
