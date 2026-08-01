package io.swerr.pocketbrowser.config;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public enum QualityPreset {
    PERFORMANCE("performance", "jpeg", 72, 24, 960),

    FLUID("fluid", "jpeg", 80, 60, 1280),

    BALANCED("balanced", "jpeg", 88, 30, 0),

    SHARP("sharp", "jpeg", 96, 20, 0),

    LOSSLESS("lossless", "png", 100, 15, 0);

    public final String id;
    public final String format;
    public final int quality;
    public final int fps;
    public final int maxWidth;

    QualityPreset(String id, String format, int quality, int fps, int maxWidth) {
        this.id = id;
        this.format = format;
        this.quality = quality;
        this.fps = fps;
        this.maxWidth = maxWidth;
    }

    public Component label() {
        return Component.translatable("pocketbrowser.preset." + id);
    }

    public Component description() {
        return Component.translatable("pocketbrowser.preset." + id + ".hint");
    }

    public void applyTo(Config config) {
        config.frameFormat = format;
        config.frameQuality = quality;
        config.maxFps = fps;
        config.maxFrameWidth = maxWidth;
    }

    public boolean matches(Config config) {
        return format.equals(config.frameFormat)
                && (quality == config.frameQuality || "png".equals(format))
                && fps == config.maxFps
                && maxWidth == config.maxFrameWidth;
    }

    @Nullable
    public static QualityPreset current(Config config) {
        for (QualityPreset preset : values()) {
            if (preset.matches(config)) {
                return preset;
            }
        }
        return null;
    }
}
