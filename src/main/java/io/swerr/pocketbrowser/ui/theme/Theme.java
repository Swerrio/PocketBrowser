package io.swerr.pocketbrowser.ui.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Theme {
    public static int WINDOW;
    public static int CHROME;
    public static int CHROME_HIGH;
    public static int FIELD;
    public static int BORDER;
    public static int BORDER_SOFT;

    public static int TEXT;
    public static int TEXT_DIM;
    public static int TEXT_FAINT;

    public static int ACCENT;
    public static int ACCENT_DIM;
    public static int PINNED;
    public static int DANGER;
    public static int SECURE;

    public static int HOVER;
    public static int SCRIM;

    private static boolean rounded = false;
    private static boolean bevelled = false;

    static {
        apply(ThemeStyle.MINECRAFT);
    }

    private Theme() {
    }

    public static void apply(ThemeStyle theme) {
        WINDOW = theme.window;
        CHROME = theme.chrome;
        CHROME_HIGH = theme.chromeHigh;
        FIELD = theme.field;
        BORDER = theme.border;
        BORDER_SOFT = theme.borderSoft;
        TEXT = theme.text;
        TEXT_DIM = theme.textDim;
        TEXT_FAINT = theme.textFaint;
        ACCENT = theme.accent;
        ACCENT_DIM = theme.accentDim;
        PINNED = theme.pinned;
        DANGER = theme.danger;
        SECURE = theme.secure;
        HOVER = theme.hover;
        SCRIM = theme.scrim;
        rounded = theme.rounded;
        bevelled = theme.bevel;
    }

    public static int fade(int argb, float factor) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.clamp(factor, 0.0F, 1.0F));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.clamp(amount, 0.0F, 1.0F);
        int a = channel(from, 24, to, t);
        int r = channel(from, 16, to, t);
        int g = channel(from, 8, to, t);
        int b = channel(from, 0, to, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int shift, int to, float t) {
        int start = (from >>> shift) & 0xFF;
        int end = (to >>> shift) & 0xFF;
        return Math.clamp(Math.round(start + (end - start) * t), 0, 255);
    }

    public static void roundedRect(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (!rounded || x1 - x0 < 4 || y1 - y0 < 4) {
            graphics.fill(x0, y0, x1, y1, color);
            bevel(graphics, x0, y0, x1, y1, color);
            return;
        }
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
        graphics.fill(x0, y0 + 2, x1, y1 - 2, color);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
    }

    private static void bevel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        bevel(graphics, x0, y0, x1, y1, color, false);
    }

    private static void bevel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                              int color, boolean sunken) {
        if (!bevelled || (color >>> 24) != 0xFF || x1 - x0 < 14 || y1 - y0 < 8) {
            return;
        }
        int light = mix(color, 0xFFFFFFFF, 0.34F);
        int dark = mix(color, 0xFF000000, 0.30F);
        int topLeft = sunken ? dark : light;
        int bottomRight = sunken ? light : dark;
        graphics.fill(x0, y0, x1 - 1, y0 + 1, topLeft);
        graphics.fill(x0, y0, x0 + 1, y1 - 1, topLeft);
        graphics.fill(x0 + 1, y1 - 1, x1, y1, bottomRight);
        graphics.fill(x1 - 1, y0 + 1, x1, y1, bottomRight);
    }

    public static boolean isVanilla() {
        return bevelled;
    }

    public static void controlSurface(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                      boolean hovered, float opacity) {
        if (bevelled) {
            roundedRect(graphics, x0, y0, x1, y1, fade(hovered ? CHROME_HIGH : CHROME, opacity));
        } else if (hovered) {
            roundedRect(graphics, x0, y0, x1, y1, fade(HOVER, opacity));
        }
    }

    public static void inset(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                             boolean focused, float opacity) {
        int fill = fade(FIELD, opacity);
        if (bevelled) {
            graphics.fill(x0, y0, x1, y1, fill);
            bevel(graphics, x0, y0, x1, y1, fill, true);
            if (focused) {
                outline(graphics, x0, y0, x1, y1, fade(ACCENT, opacity));
            }
        } else {
            roundedRect(graphics, x0, y0, x1, y1, fill);
            roundedOutline(graphics, x0, y0, x1, y1,
                    fade(focused ? ACCENT : BORDER_SOFT, opacity));
        }
    }

    public static void roundedOutline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (!rounded || x1 - x0 < 4 || y1 - y0 < 4) {
            outline(graphics, x0, y0, x1, y1, color);
            return;
        }
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
        graphics.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        graphics.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, color);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, color);
        graphics.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, color);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, color);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    public static void shadow(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float opacity) {
        int[] alphas = {0x30, 0x22, 0x16, 0x0C};
        for (int ring = 0; ring < alphas.length; ring++) {
            int spread = ring + 1;
            int color = fade(alphas[ring] << 24, opacity);
            if (color == 0) {
                continue;
            }
            int left = x0 - spread;
            int top = y0 - spread + 1;
            int right = x1 + spread;
            int bottom = y1 + spread + 1;
            graphics.fill(left, top, right, top + 1, color);
            graphics.fill(left, bottom - 1, right, bottom, color);
            graphics.fill(left, top + 1, left + 1, bottom - 1, color);
            graphics.fill(right - 1, top + 1, right, bottom - 1, color);
        }
    }

    public static void window(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float opacity) {
        shadow(graphics, x0, y0, x1, y1, opacity);
        roundedRect(graphics, x0, y0, x1, y1, fade(WINDOW, opacity));
        roundedOutline(graphics, x0, y0, x1, y1, fade(BORDER, opacity));
    }

    public static void separator(GuiGraphicsExtractor graphics, int x0, int x1, int y, float opacity) {
        graphics.fill(x0, y, x1, y + 1, fade(BORDER_SOFT, opacity));
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int room = maxWidth - font.width("...");
        if (room <= 0) {
            return "";
        }
        return font.plainSubstrByWidth(text, room) + "...";
    }
}
