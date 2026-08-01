package io.swerr.pocketbrowser.ui.widget;

import io.swerr.pocketbrowser.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Spinner {
    private static final int DOTS = 8;
    private static final int RADIUS = 7;
    private static final long PERIOD_MILLIS = 900L;

    private Spinner() {
    }

    public static void draw(GuiGraphicsExtractor graphics, int centreX, int centreY, int color) {
        float phase = (System.currentTimeMillis() % PERIOD_MILLIS) / (float) PERIOD_MILLIS;
        for (int i = 0; i < DOTS; i++) {
            double angle = Math.TAU * i / DOTS;
            int x = centreX + (int) Math.round(Math.sin(angle) * RADIUS);
            int y = centreY - (int) Math.round(Math.cos(angle) * RADIUS);

            float offset = (phase + (float) i / DOTS) % 1.0F;
            float brightness = 0.15F + 0.85F * (1.0F - offset) * (1.0F - offset);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, Theme.fade(color, brightness));
        }
    }

    public static void progressBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                   int color, float opacity) {
        int width = x1 - x0;
        if (width <= 0) {
            return;
        }
        graphics.fill(x0, y0, x1, y1, Theme.fade(color, opacity * 0.18F));

        int band = Math.max(24, width / 5);
        float travel = (System.currentTimeMillis() % 1400L) / 1400.0F;

        int head = x0 - band + Math.round(travel * (width + band));
        int left = Math.max(x0, head);
        int right = Math.min(x1, head + band);
        if (right > left) {
            graphics.fill(left, y0, right, y1, Theme.fade(color, opacity));
        }
    }
}
