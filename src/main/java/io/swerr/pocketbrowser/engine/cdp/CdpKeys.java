package io.swerr.pocketbrowser.engine.cdp;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class CdpKeys {
    public record Key(int windowsVirtualKeyCode, String key, String code, @Nullable String text) {
    }

    private static final Map<Integer, Key> KEYS = new HashMap<>();

    private CdpKeys() {
    }

    static {
        put(GLFW.GLFW_KEY_BACKSPACE, 8, "Backspace", "Backspace", null);
        put(GLFW.GLFW_KEY_TAB, 9, "Tab", "Tab", "\t");
        put(GLFW.GLFW_KEY_ENTER, 13, "Enter", "Enter", "\r");
        put(GLFW.GLFW_KEY_KP_ENTER, 13, "Enter", "NumpadEnter", "\r");
        put(GLFW.GLFW_KEY_ESCAPE, 27, "Escape", "Escape", null);
        put(GLFW.GLFW_KEY_SPACE, 32, " ", "Space", " ");
        put(GLFW.GLFW_KEY_PAGE_UP, 33, "PageUp", "PageUp", null);
        put(GLFW.GLFW_KEY_PAGE_DOWN, 34, "PageDown", "PageDown", null);
        put(GLFW.GLFW_KEY_END, 35, "End", "End", null);
        put(GLFW.GLFW_KEY_HOME, 36, "Home", "Home", null);
        put(GLFW.GLFW_KEY_LEFT, 37, "ArrowLeft", "ArrowLeft", null);
        put(GLFW.GLFW_KEY_UP, 38, "ArrowUp", "ArrowUp", null);
        put(GLFW.GLFW_KEY_RIGHT, 39, "ArrowRight", "ArrowRight", null);
        put(GLFW.GLFW_KEY_DOWN, 40, "ArrowDown", "ArrowDown", null);
        put(GLFW.GLFW_KEY_INSERT, 45, "Insert", "Insert", null);
        put(GLFW.GLFW_KEY_DELETE, 46, "Delete", "Delete", null);

        for (int i = 0; i < 12; i++) {
            String name = "F" + (i + 1);
            put(GLFW.GLFW_KEY_F1 + i, 112 + i, name, name, null);
        }

        for (char c = 'A'; c <= 'Z'; c++) {
            put(c, c, String.valueOf(Character.toLowerCase(c)), "Key" + c, null);
        }
        for (char c = '0'; c <= '9'; c++) {
            put(c, c, String.valueOf(c), "Digit" + c, null);
        }
    }

    private static void put(int glfw, int vk, String key, String code, @Nullable String text) {
        KEYS.put(glfw, new Key(vk, key, code, text));
    }

    @Nullable
    public static Key of(int glfwKey) {
        return KEYS.get(glfwKey);
    }

    public static int modifiers(int glfwModifiers) {
        int out = 0;
        if ((glfwModifiers & GLFW.GLFW_MOD_ALT) != 0) {
            out |= 1;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            out |= 2;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_SUPER) != 0) {
            out |= 4;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            out |= 8;
        }
        return out;
    }

    public static String mouseButton(int button) {
        return switch (button) {
            case 0 -> "left";
            case 1 -> "right";
            case 2 -> "middle";
            case 3 -> "back";
            case 4 -> "forward";
            default -> "none";
        };
    }

    public static int buttonMask(int button) {
        return switch (button) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            case 4 -> 16;
            default -> 0;
        };
    }
}
