package io.swerr.pocketbrowser.ui.widget;

import io.swerr.pocketbrowser.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class UrlBar {
    private static final int PADDING = 4;

    private String text = "";
    private int cursor;
    private int anchor;

    private int scroll;
    private boolean focused;
    private long caretResetMillis = System.currentTimeMillis();

    private int textLeft;
    private int textRight;

    public String text() {
        return text;
    }

    public void setText(String value) {
        text = value == null ? "" : value;
        cursor = Math.min(cursor, text.length());
        anchor = Math.min(anchor, text.length());
        scroll = Math.min(scroll, text.length());
        caretResetMillis = System.currentTimeMillis();
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean value) {
        if (focused == value) {
            return;
        }
        focused = value;
        caretResetMillis = System.currentTimeMillis();
        if (!value) {
            anchor = cursor;
        }
    }

    public void focusAndSelectAll() {
        setFocused(true);
        anchor = 0;
        cursor = text.length();
        scroll = 0;
    }

    public boolean hasSelection() {
        return cursor != anchor;
    }

    private int selectionStart() {
        return Math.min(cursor, anchor);
    }

    private int selectionEnd() {
        return Math.max(cursor, anchor);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int x1, int y1,
                       float opacity, String placeholder) {
        textLeft = x0 + PADDING;
        textRight = x1 - PADDING;
        int inner = Math.max(1, textRight - textLeft);
        int baseline = y0 + (y1 - y0 - font.lineHeight) / 2 + 1;

        keepCursorVisible(font, inner);

        String tail = text.substring(Math.min(scroll, text.length()));
        String visible = font.plainSubstrByWidth(tail, inner);

        if (text.isEmpty() && !placeholder.isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(placeholder, inner),
                    textLeft, baseline, Theme.fade(Theme.TEXT_FAINT, opacity), false);
        }

        if (focused && hasSelection()) {
            int from = Math.clamp(selectionStart() - scroll, 0, visible.length());
            int to = Math.clamp(selectionEnd() - scroll, 0, visible.length());
            if (to > from) {
                int left = textLeft + font.width(visible.substring(0, from));
                int right = textLeft + font.width(visible.substring(0, to));
                graphics.fill(left, y0 + 2, Math.min(right, textRight), y1 - 2,
                        Theme.fade(Theme.ACCENT_DIM, opacity * 0.75F));
            }
        }

        if (!visible.isEmpty()) {
            graphics.text(font, visible, textLeft, baseline, Theme.fade(Theme.TEXT, opacity), false);
        }

        if (focused && (System.currentTimeMillis() - caretResetMillis) / 500L % 2L == 0L) {
            int offset = Math.clamp(cursor - scroll, 0, visible.length());
            int caretX = textLeft + font.width(visible.substring(0, offset));
            graphics.fill(Math.min(caretX, textRight - 1), y0 + 2,
                    Math.min(caretX + 1, textRight), y1 - 2, Theme.fade(Theme.ACCENT, opacity));
        }
    }

    private void keepCursorVisible(Font font, int innerWidth) {
        scroll = Math.clamp(scroll, 0, text.length());
        if (cursor < scroll) {
            scroll = cursor;
        }
        while (scroll < cursor && font.width(text.substring(scroll, cursor)) > innerWidth) {
            scroll++;
        }

        while (scroll > 0 && font.width(text.substring(scroll - 1)) <= innerWidth) {
            scroll--;
        }
    }

    public void placeCursorAt(Font font, double mouseX, boolean extendSelection) {
        int index = indexAt(font, mouseX);
        cursor = index;
        if (!extendSelection) {
            anchor = index;
        }
        caretResetMillis = System.currentTimeMillis();
    }

    private int indexAt(Font font, double mouseX) {
        String tail = text.substring(Math.min(scroll, text.length()));
        int relative = (int) Math.round(mouseX - textLeft);
        if (relative <= 0) {
            return scroll;
        }
        String fitted = font.plainSubstrByWidth(tail, Math.max(0, relative));

        if (fitted.length() < tail.length()) {
            int here = font.width(fitted);
            int next = font.width(tail.substring(0, fitted.length() + 1));
            if (relative - here > next - relative) {
                return scroll + fitted.length() + 1;
            }
        }
        return scroll + fitted.length();
    }

    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        caretResetMillis = System.currentTimeMillis();

        if (event.isSelectAll()) {
            anchor = 0;
            cursor = text.length();
            return true;
        }
        if (event.isCopy()) {
            copySelection();
            return true;
        }
        if (event.isCut()) {
            copySelection();
            deleteSelection();
            return true;
        }
        if (event.isPaste()) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();

            insert(clipboard.replace("\r", "").replace("\n", " ").trim());
            return true;
        }

        boolean shift = event.hasShiftDown();
        boolean control = event.hasControlDown();

        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                move(control ? previousWord() : cursor - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                move(control ? nextWord() : cursor + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                move(0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                move(text.length(), shift);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursor > 0) {
                    int from = control ? previousWord() : cursor - 1;
                    text = text.substring(0, from) + text.substring(cursor);
                    cursor = from;
                    anchor = from;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursor < text.length()) {
                    int to = control ? nextWord() : cursor + 1;
                    text = text.substring(0, cursor) + text.substring(to);
                    anchor = cursor;
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean charTyped(int codepoint) {
        if (!focused || Character.isISOControl(codepoint)) {
            return false;
        }
        insert(Character.toString(codepoint));
        return true;
    }

    private void insert(String value) {
        if (value.isEmpty()) {
            return;
        }
        if (hasSelection()) {
            deleteSelection();
        }
        text = text.substring(0, cursor) + value + text.substring(cursor);
        cursor += value.length();
        anchor = cursor;
        caretResetMillis = System.currentTimeMillis();
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        int from = selectionStart();
        int to = selectionEnd();
        text = text.substring(0, from) + text.substring(to);
        cursor = from;
        anchor = from;
    }

    private void copySelection() {
        if (hasSelection()) {
            Minecraft.getInstance().keyboardHandler
                    .setClipboard(text.substring(selectionStart(), selectionEnd()));
        }
    }

    private void move(int to, boolean extendSelection) {
        cursor = Math.clamp(to, 0, text.length());
        if (!extendSelection) {
            anchor = cursor;
        }
    }

    private int previousWord() {
        int index = cursor;
        while (index > 0 && !Character.isLetterOrDigit(text.charAt(index - 1))) {
            index--;
        }
        while (index > 0 && Character.isLetterOrDigit(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private int nextWord() {
        int index = cursor;
        int length = text.length();
        while (index < length && !Character.isLetterOrDigit(text.charAt(index))) {
            index++;
        }
        while (index < length && Character.isLetterOrDigit(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
