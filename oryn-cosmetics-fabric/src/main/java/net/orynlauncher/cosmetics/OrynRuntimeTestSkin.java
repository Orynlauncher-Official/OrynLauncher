package net.orynlauncher.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class OrynRuntimeTestSkin {
    private static File cached;

    private OrynRuntimeTestSkin() {}

    public static synchronized File ensure() {
        if (cached != null && cached.isFile()) return cached;
        try {
            File dir = new File(new File(System.getProperty("user.dir", ".")),
                    ".oryn/cosmetics/runtime-cache");
            dir.mkdirs();
            cached = new File(dir, "oryn-debug-test-skin-64x64.png");
            if (!cached.isFile() || cached.length() == 0) {
                writePng(cached);
            }
            System.out.println("[ORYN-COSMETICS] TEST SKIN enabled: generated 64x64 RGBA diagnostic texture");
            return cached;
        } catch (Throwable t) {
            System.out.println("[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: test texture generation failed: " + t);
            return null;
        }
    }

    private static void writePng(File file) throws Exception {
        final int w = 64, h = 64;
        byte[] raw = new byte[h * (1 + w * 4)];
        int p = 0;
        for (int y = 0; y < h; y++) {
            raw[p++] = 0;
            for (int x = 0; x < w; x++) {
                int argb = colorFor(x, y);
                raw[p++] = (byte) ((argb >>> 24) & 0xFF);
                raw[p++] = (byte) ((argb >>> 16) & 0xFF);
                raw[p++] = (byte) ((argb >>> 8) & 0xFF);
                raw[p++] = (byte) (argb & 0xFF);
            }
        }

        Deflater deflater = new Deflater(6);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            compressed.write(buffer, 0, n);
        }
        deflater.end();

        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
            writeChunk(out, "IHDR", new byte[]{
                    0,0,0,64, 0,0,0,64, 8,6,0,0,0
            });
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    private static int colorFor(int x, int y) {
        if (inside(x, y, 0, 0, 32, 16)) return 0xFFFF0000;   // head: red
        if (inside(x, y, 16, 16, 40, 32)) return 0xFF00FF00; // torso: green
        if (inside(x, y, 0, 16, 16, 32) || inside(x, y, 16, 48, 32, 64)) return 0xFF0000FF; // legs
        if (inside(x, y, 40, 16, 56, 32) || inside(x, y, 32, 48, 48, 64)) return 0xFFFFFF00; // arms
        return 0x00000000;
    }

    private static boolean inside(int x, int y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x < x2 && y >= y1 && y < y2;
    }

    private static void writeChunk(FileOutputStream out, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(intBytes(data.length));
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(intBytes((int) crc.getValue()));
    }

    private static byte[] intBytes(int value) {
        return new byte[]{
                (byte)(value >>> 24), (byte)(value >>> 16),
                (byte)(value >>> 8), (byte)value
        };
    }
}
