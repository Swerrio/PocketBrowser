package io.swerr.pocketbrowser.engine;

public record Viewport(int frameWidth, int frameHeight, double zoom) {
    public static Viewport of(int guiWidth, int guiHeight, int guiScale, double zoom, int maxFrameWidth) {
        int width = Math.max(1, guiWidth * Math.max(1, guiScale));
        int height = Math.max(1, guiHeight * Math.max(1, guiScale));

        if (maxFrameWidth > 0 && width > maxFrameWidth) {
            height = Math.max(1, (int) Math.round((double) height * maxFrameWidth / width));
            width = maxFrameWidth;
        }

        double safeZoom = zoom <= 0.0D ? 1.0D : Math.round(zoom * 100.0D) / 100.0D;
        return new Viewport(width, height, safeZoom);
    }

    public boolean matches(Viewport other) {
        return other != null
                && other.frameWidth == frameWidth
                && other.frameHeight == frameHeight
                && other.zoom == zoom;
    }
}
