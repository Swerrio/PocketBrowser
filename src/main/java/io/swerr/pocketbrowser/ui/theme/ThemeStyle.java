package io.swerr.pocketbrowser.ui.theme;

import java.util.Locale;

import net.minecraft.network.chat.Component;

public enum ThemeStyle {
    MINECRAFT("minecraft", false, true,
            0xFFC6C6C6, 0xFFB2B2B2, 0xFFDBDBDB, 0xFFEFEFEF, 0xFF373737, 0xFF8B8B8B,
            0xFF2F2F2F, 0xFF565656, 0xFF767676,
            0xFF3C8527, 0xFF9BC48D, 0xFFC07613, 0xFFB02E26, 0xFF3C8527,
            0x28000000, 0xC0101010),

    DARK("dark", true, false,
            0xFF14161C, 0xFF1C2029, 0xFF232833, 0xFF0E1015, 0xFF2E3440, 0xFF262B35,
            0xFFE6E9EF, 0xFF8D94A3, 0xFF5A6172,
            0xFF4FA3FF, 0xFF2C6BB0, 0xFFFFC24A, 0xFFFF6B6B, 0xFF66D19E,
            0x22FFFFFF, 0xB8000000),

    LIGHT("light", true, false,
            0xFFFFFFFF, 0xFFF1F3F4, 0xFFFFFFFF, 0xFFEDEEF0, 0xFFDADCE0, 0xFFE8EAED,
            0xFF202124, 0xFF5F6368, 0xFF9AA0A6,
            0xFF1A73E8, 0xFFA8C7FA, 0xFFE37400, 0xFFD93025, 0xFF1E8E3E,
            0x14000000, 0x80000000);

    public final String id;

    public final boolean rounded;

    public final boolean bevel;

    final int window;
    final int chrome;
    final int chromeHigh;
    final int field;
    final int border;
    final int borderSoft;
    final int text;
    final int textDim;
    final int textFaint;
    final int accent;
    final int accentDim;
    final int pinned;
    final int danger;
    final int secure;
    final int hover;
    final int scrim;

    ThemeStyle(String id, boolean rounded, boolean bevel,
            int window, int chrome, int chromeHigh, int field, int border, int borderSoft,
            int text, int textDim, int textFaint,
            int accent, int accentDim, int pinned, int danger, int secure,
            int hover, int scrim) {
        this.id = id;
        this.rounded = rounded;
        this.bevel = bevel;
        this.window = window;
        this.chrome = chrome;
        this.chromeHigh = chromeHigh;
        this.field = field;
        this.border = border;
        this.borderSoft = borderSoft;
        this.text = text;
        this.textDim = textDim;
        this.textFaint = textFaint;
        this.accent = accent;
        this.accentDim = accentDim;
        this.pinned = pinned;
        this.danger = danger;
        this.secure = secure;
        this.hover = hover;
        this.scrim = scrim;
    }

    public Component label() {
        return Component.translatable("pocketbrowser.theme." + id);
    }

    public static ThemeStyle from(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (ThemeStyle theme : values()) {
            if (theme.id.equals(wanted)) {
                return theme;
            }
        }
        return MINECRAFT;
    }

}
