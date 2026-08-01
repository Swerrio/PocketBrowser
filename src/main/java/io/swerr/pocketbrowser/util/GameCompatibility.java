package io.swerr.pocketbrowser.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.swerr.pocketbrowser.PocketBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public final class GameCompatibility {
    private static final Method GUI_SCREEN = method(Gui.class, "screen");
    private static final Method GUI_SET_SCREEN = method(Gui.class, "setScreen", Screen.class);
    private static final Field GUI_HUD = field(Gui.class, "hud");
    private static final Method HUD_IS_HIDDEN = GUI_HUD == null
            ? null : method(GUI_HUD.getType(), "isHidden");

    private static final Field MC_SCREEN = field(Minecraft.class, "screen");
    private static final Method MC_SET_SCREEN = method(Minecraft.class, "setScreen", Screen.class);
    private static final Field HIDE_GUI = field(optionsType(), "hideGui");

    static {
        if (GUI_SCREEN == null && MC_SCREEN == null) {
            PocketBrowser.LOG.error("This game version exposes neither Gui.screen() nor "
                    + "Minecraft.screen; the browser will not know which screen is open");
        }
        if (HUD_IS_HIDDEN == null && HIDE_GUI == null) {
            PocketBrowser.LOG.warn("Cannot tell whether the HUD is hidden on this game version; "
                    + "the pinned window will stay visible when the HUD is turned off");
        }
    }

    private GameCompatibility() {
    }

    @Nullable
    public static Screen screen(Minecraft minecraft) {
        Object value = GUI_SCREEN != null
                ? call(GUI_SCREEN, minecraft.gui)
                : read(MC_SCREEN, minecraft);
        return value instanceof Screen shown ? shown : null;
    }

    public static void openScreen(Minecraft minecraft, @Nullable Screen screen) {
        if (GUI_SET_SCREEN != null) {
            call(GUI_SET_SCREEN, minecraft.gui, screen);
        } else if (MC_SET_SCREEN != null) {
            call(MC_SET_SCREEN, minecraft, screen);
        } else {
            minecraft.setScreenAndShow(screen);
        }
    }

    public static boolean hudHidden(Minecraft minecraft) {
        if (HUD_IS_HIDDEN != null && GUI_HUD != null) {
            Object hud = read(GUI_HUD, minecraft.gui);
            return hud != null && Boolean.TRUE.equals(call(HUD_IS_HIDDEN, hud));
        }
        return Boolean.TRUE.equals(read(HIDE_GUI, minecraft.options));
    }

    @Nullable
    private static Class<?> optionsType() {
        try {
            return Minecraft.class.getField("options").getType();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Nullable
    private static Method method(@Nullable Class<?> owner, String name, Class<?>... arguments) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, arguments);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Nullable
    private static Field field(@Nullable Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getField(name);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Nullable
    private static Object read(@Nullable Field field, @Nullable Object owner) {
        if (field == null || owner == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            PocketBrowser.LOG.warn("Could not read {}", field.getName(), e);
            return null;
        }
    }

    @Nullable
    private static Object call(@Nullable Method target, @Nullable Object owner, Object... arguments) {
        if (target == null || owner == null) {
            return null;
        }
        try {
            return target.invoke(owner, arguments);
        } catch (ReflectiveOperationException e) {
            PocketBrowser.LOG.warn("Could not call {}", target.getName(), e);
            return null;
        }
    }
}
