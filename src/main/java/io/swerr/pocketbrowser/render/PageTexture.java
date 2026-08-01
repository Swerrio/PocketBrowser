package io.swerr.pocketbrowser.render;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import io.swerr.pocketbrowser.PocketBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class PageTexture implements AutoCloseable {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private record Pending(NativeImage image, long sequence) {
    }

    private final AtomicReference<Pending> incoming = new AtomicReference<>();
    private final Identifier name = PocketBrowser.id("page/" + NAMES.incrementAndGet());
    private volatile long shown;

    private static final class SmoothTexture extends DynamicTexture {
        private SmoothTexture(NativeImage image) {
            super(() -> "Pocket Browser page", image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    @Nullable
    private DynamicTexture texture;
    private int width;
    private int height;
    private volatile boolean disposed;

    public void offer(NativeImage frame, long sequence) {
        if (disposed || sequence <= shown) {
            frame.close();
            return;
        }
        Pending next = new Pending(frame, sequence);
        while (true) {
            Pending current = incoming.get();
            if (current != null && current.sequence >= sequence) {
                frame.close();
                return;
            }
            if (incoming.compareAndSet(current, next)) {
                if (current != null) {
                    current.image.close();
                }
                break;
            }
        }
        if (disposed) {
            drop();
        }
    }

    public boolean sync() {
        Pending frame = incoming.getAndSet(null);
        if (frame != null) {
            if (frame.sequence <= shown) {
                frame.image.close();
            } else {
                shown = frame.sequence;
                try {
                    upload(frame.image);
                } catch (RuntimeException e) {
                    PocketBrowser.LOG.warn("Could not upload a browser frame", e);
                }
            }
        }
        return texture != null;
    }

    private void drop() {
        Pending stranded = incoming.getAndSet(null);
        if (stranded != null) {
            stranded.image.close();
        }
    }

    private void upload(NativeImage frame) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        int frameWidth = frame.getWidth();
        int frameHeight = frame.getHeight();

        if (texture != null && (frameWidth != width || frameHeight != height)) {
            textures.release(name);
            texture = null;
        }

        if (texture != null) {
            texture.setPixels(frame);
            texture.upload();
            return;
        }

        SmoothTexture created;
        try {
            created = new SmoothTexture(frame);
        } catch (RuntimeException e) {
            frame.close();
            throw e;
        }
        try {
            textures.register(name, created);
        } catch (RuntimeException e) {
            created.close();
            throw e;
        }
        texture = created;
        width = frameWidth;
        height = frameHeight;
    }

    public Identifier name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        disposed = true;
        drop();
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(name);
            texture = null;
        }
        width = 0;
        height = 0;
    }
}
