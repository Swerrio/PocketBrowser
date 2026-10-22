package io.swerr.pocketbrowser.engine.cdp;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;

public final class CdpKeys {
    public record Key(int windowsVirtualKeyCode, String key, String code, @Nullable String text) {
    }

    private static final Map<Integer, Key> KEYS = new HashMap<>();

    private CdpKeys() {
    }

    static {
        put(SDLScancode.SDL_SCANCODE_BACKSPACE, 8, "Backspace", "Backspace", null);
        put(SDLScancode.SDL_SCANCODE_TAB, 9, "Tab", "Tab", "\t");
        put(SDLScancode.SDL_SCANCODE_RETURN, 13, "Enter", "Enter", "\r");
        put(SDLScancode.SDL_SCANCODE_KP_ENTER, 13, "Enter", "NumpadEnter", "\r");
        put(SDLScancode.SDL_SCANCODE_ESCAPE, 27, "Escape", "Escape", null);
        put(SDLScancode.SDL_SCANCODE_SPACE, 32, " ", "Space", " ");
        put(SDLScancode.SDL_SCANCODE_PAGEUP, 33, "PageUp", "PageUp", null);
        put(SDLScancode.SDL_SCANCODE_PAGEDOWN, 34, "PageDown", "PageDown", null);
        put(SDLScancode.SDL_SCANCODE_END, 35, "End", "End", null);
        put(SDLScancode.SDL_SCANCODE_HOME, 36, "Home", "Home", null);
        put(SDLScancode.SDL_SCANCODE_LEFT, 37, "ArrowLeft", "ArrowLeft", null);
        put(SDLScancode.SDL_SCANCODE_UP, 38, "ArrowUp", "ArrowUp", null);
        put(SDLScancode.SDL_SCANCODE_RIGHT, 39, "ArrowRight", "ArrowRight", null);
        put(SDLScancode.SDL_SCANCODE_DOWN, 40, "ArrowDown", "ArrowDown", null);
        put(SDLScancode.SDL_SCANCODE_INSERT, 45, "Insert", "Insert", null);
        put(SDLScancode.SDL_SCANCODE_DELETE, 46, "Delete", "Delete", null);

        for (int i = 0; i < 12; i++) {
            String name = "F" + (i + 1);
            put(SDLScancode.SDL_SCANCODE_F1 + i, 112 + i, name, name, null);
        }

        for (char c = 'A'; c <= 'Z'; c++) {
            put(SDLScancode.SDL_SCANCODE_A + (c - 'A'), c, String.valueOf(Character.toLowerCase(c)), "Key" + c, null);
        }
        for (char c = '0'; c <= '9'; c++) {
            int scancode = c == '0' ? SDLScancode.SDL_SCANCODE_0 : SDLScancode.SDL_SCANCODE_1 + (c - '1');
            put(scancode, c, String.valueOf(c), "Digit" + c, null);
        }
    }

    private static void put(int scancode, int vk, String key, String code, @Nullable String text) {
        KEYS.put(scancode, new Key(vk, key, code, text));
    }

    @Nullable
    public static Key of(int scancode) {
        return KEYS.get(scancode);
    }

    public static int modifiers(int sdlModifiers) {
        int out = 0;
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_ALT) != 0) {
            out |= 1;
        }
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_CTRL) != 0) {
            out |= 2;
        }
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_GUI) != 0) {
            out |= 4;
        }
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_SHIFT) != 0) {
            out |= 8;
        }
        return out;
    }

    public static String mouseButton(int button) {
        return switch (button) {
            case SDLMouse.SDL_BUTTON_LEFT -> "left";
            case SDLMouse.SDL_BUTTON_RIGHT -> "right";
            case SDLMouse.SDL_BUTTON_MIDDLE -> "middle";
            case SDLMouse.SDL_BUTTON_X1 -> "back";
            case SDLMouse.SDL_BUTTON_X2 -> "forward";
            default -> "none";
        };
    }

    public static int buttonMask(int button) {
        return switch (button) {
            case SDLMouse.SDL_BUTTON_LEFT -> 1;
            case SDLMouse.SDL_BUTTON_RIGHT -> 2;
            case SDLMouse.SDL_BUTTON_MIDDLE -> 4;
            case SDLMouse.SDL_BUTTON_X1 -> 8;
            case SDLMouse.SDL_BUTTON_X2 -> 16;
            default -> 0;
        };
    }
}
