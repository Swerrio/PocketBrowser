package io.swerr.pocketbrowser.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;

public final class FrameDecoder {
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        private BufferedImage image;
        private String format = "";
        private int[] pixels;

        private BufferedImage destinationFor(String wanted, int width, int height) {
            if (image != null && format.equals(wanted)
                    && image.getWidth() == width && image.getHeight() == height) {
                return image;
            }
            return null;
        }

        private void remember(BufferedImage decoded, String wanted) {
            image = decoded;
            format = wanted;
        }

        private int[] pixels(int count) {
            if (pixels == null || pixels.length != count) {
                pixels = new int[count];
            }
            return pixels;
        }
    }

    private FrameDecoder() {
    }

    public static NativeImage decode(byte[] data) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("no image reader accepted this frame");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new IOException("frame reported a size of " + width + "x" + height);
                }

                String format = reader.getFormatName();
                Scratch scratch = SCRATCH.get();
                ImageReadParam parameters = reader.getDefaultReadParam();
                BufferedImage reuse = scratch.destinationFor(format, width, height);
                if (reuse != null) {
                    parameters.setDestination(reuse);
                }

                BufferedImage decoded = reader.read(0, parameters);
                scratch.remember(decoded, format);

                int count = width * height;
                int[] pixels = scratch.pixels(count);
                toNativeOrder(decoded, pixels, count);

                NativeImage image = new NativeImage(width, height, false);
                try {
                    MemoryUtil.memIntBuffer(image.getPointer(), count).put(pixels);
                } catch (RuntimeException e) {
                    image.close();
                    throw new IOException("could not fill the frame image", e);
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static void toNativeOrder(BufferedImage source, int[] out, int count) {
        switch (source.getType()) {
            case BufferedImage.TYPE_3BYTE_BGR -> {
                byte[] bytes = ((DataBufferByte) source.getRaster().getDataBuffer()).getData();
                for (int i = 0, at = 0; i < count; i++, at += 3) {
                    out[i] = 0xFF000000
                            | ((bytes[at] & 0xFF) << 16)
                            | ((bytes[at + 1] & 0xFF) << 8)
                            | (bytes[at + 2] & 0xFF);
                }
            }
            case BufferedImage.TYPE_4BYTE_ABGR -> {
                byte[] bytes = ((DataBufferByte) source.getRaster().getDataBuffer()).getData();
                for (int i = 0, at = 0; i < count; i++, at += 4) {
                    out[i] = 0xFF000000
                            | ((bytes[at + 1] & 0xFF) << 16)
                            | ((bytes[at + 2] & 0xFF) << 8)
                            | (bytes[at + 3] & 0xFF);
                }
            }
            case BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB -> {
                int[] argb = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();
                for (int i = 0; i < count; i++) {
                    int colour = argb[i];
                    out[i] = 0xFF000000
                            | ((colour & 0xFF) << 16)
                            | (colour & 0xFF00)
                            | ((colour >>> 16) & 0xFF);
                }
            }
            default -> {
                int width = source.getWidth();
                for (int i = 0; i < count; i++) {
                    int colour = source.getRGB(i % width, i / width);
                    out[i] = 0xFF000000
                            | ((colour & 0xFF) << 16)
                            | (colour & 0xFF00)
                            | ((colour >>> 16) & 0xFF);
                }
            }
        }
    }
}
