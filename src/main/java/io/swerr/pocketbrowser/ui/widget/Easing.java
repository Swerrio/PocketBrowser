package io.swerr.pocketbrowser.ui.widget;

public final class Easing {
    private Easing() {
    }

    public static float outCubic(float t) {
        float x = 1.0F - Math.clamp(t, 0.0F, 1.0F);
        return 1.0F - x * x * x;
    }

    public static float inCubic(float t) {
        float x = Math.clamp(t, 0.0F, 1.0F);
        return x * x * x;
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * Math.clamp(amount, 0.0F, 1.0F);
    }
}
