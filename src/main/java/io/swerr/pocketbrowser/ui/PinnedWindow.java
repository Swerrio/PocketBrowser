package io.swerr.pocketbrowser.ui;

import io.swerr.pocketbrowser.client.PocketBrowserClient;
import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.engine.BrowserTab;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.ui.theme.Theme;
import io.swerr.pocketbrowser.ui.widget.Icons;
import io.swerr.pocketbrowser.ui.widget.PagePanel;
import io.swerr.pocketbrowser.ui.widget.Rect;
import io.swerr.pocketbrowser.util.Urls;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PinnedWindow {
    private static final int TITLE_HEIGHT = 13;
    private static final int MEDIA_HEIGHT = 13;
    private static final int MIN_WIDTH = 120;
    private static final int MIN_HEIGHT = 80;

    public static final int GRIP = 10;

    private static final long MEDIA_LINGER_MILLIS = 2600L;
    private static final long MEDIA_FADE_MILLIS = 400L;

    private static long mediaShownUntil;
    private static boolean mediaWasPresent;
    private static float lastMediaVisibility;

    private PinnedWindow() {
    }

    public static void nudgeMedia() {
        mediaShownUntil = System.currentTimeMillis() + MEDIA_LINGER_MILLIS;
    }

    public static float mediaVisibility(Rect frame, BrowserTab.Media media, boolean controlMode,
                                        int mouseX, int mouseY) {
        if (!media.present()) {
            mediaWasPresent = false;
            return 0.0F;
        }
        if (!mediaWasPresent) {
            mediaWasPresent = true;
            nudgeMedia();
        }
        if (media.paused() || (controlMode && pageArea(frame).contains(mouseX, mouseY))) {
            return 1.0F;
        }
        long left = mediaShownUntil - System.currentTimeMillis();
        if (left <= 0L) {
            return 0.0F;
        }
        return left >= MEDIA_FADE_MILLIS ? 1.0F : left / (float) MEDIA_FADE_MILLIS;
    }

    public static Rect frame(int guiWidth, int guiHeight) {
        Config config = Config.get();
        int width = Math.clamp(config.pinWidth, MIN_WIDTH, Math.max(MIN_WIDTH, guiWidth - 4));
        int height = Math.clamp(config.pinHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, guiHeight - 4));
        int x = Math.clamp(config.pinX, 2, Math.max(2, guiWidth - width - 2));
        int y = Math.clamp(config.pinY, 2, Math.max(2, guiHeight - height - 2));
        return new Rect(x, y, x + width, y + height);
    }

    public static Rect titleBar(Rect frame) {
        return new Rect(frame.x0(), frame.y0(), frame.x1(), frame.y0() + TITLE_HEIGHT);
    }

    public static Rect pageArea(Rect frame) {
        return new Rect(frame.x0() + 1, frame.y0() + TITLE_HEIGHT, frame.x1() - 1, frame.y1() - 1);
    }

    public static Rect mediaBar(Rect frame) {
        Rect page = pageArea(frame);
        return new Rect(page.x0(), page.y1() - MEDIA_HEIGHT, page.x1(), page.y1());
    }

    public static Rect mediaButton(Rect frame) {
        Rect bar = mediaBar(frame);
        return new Rect(bar.x0() + 1, bar.y0(), bar.x0() + 15, bar.y1());
    }

    public static Rect mediaTrack(Rect frame) {
        Rect bar = mediaBar(frame);
        return new Rect(mediaButton(frame).x1() + 2, bar.y0() + 5, bar.x1() - 34, bar.y0() + 8);
    }

    public static boolean mediaBarHit(Rect frame, double mouseX, double mouseY) {
        return lastMediaVisibility > 0.0F && mediaBar(frame).contains(mouseX, mouseY);
    }

    public static Rect gripArea(Rect frame) {
        return new Rect(frame.x1() - GRIP, frame.y1() - GRIP, frame.x1(), frame.y1());
    }

    public static Rect expandButton(Rect frame) {
        int y = frame.y0() + 1;
        return new Rect(frame.x1() - 24, y, frame.x1() - 13, y + 11);
    }

    public static Rect unpinButton(Rect frame) {
        int y = frame.y0() + 1;
        return new Rect(frame.x1() - 12, y, frame.x1() - 1, y + 11);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Rect frame, PagePanel panel,
                            boolean controlMode, int mouseX, int mouseY) {
        Config config = Config.get();
        float opacity = (float) config.pinOpacity;
        if (!controlMode && config.pinDimWhenUnfocused) {
            opacity *= 0.82F;
        }

        Theme.shadow(graphics, frame.x0(), frame.y0(), frame.x1(), frame.y1(), opacity * 0.9F);
        Theme.roundedRect(graphics, frame.x0(), frame.y0(), frame.x1(), frame.y1(),
                Theme.fade(Theme.WINDOW, opacity));
        Theme.roundedOutline(graphics, frame.x0(), frame.y0(), frame.x1(), frame.y1(),
                Theme.fade(controlMode ? Theme.ACCENT : Theme.BORDER, opacity));

        Rect title = titleBar(frame);
        graphics.fill(title.x0() + 1, title.y0() + 1, title.x1() - 1, title.y1(),
                Theme.fade(Theme.CHROME, opacity));

        Browser browser = Browser.get();
        BrowserTab tab = browser.activeTab();

        drawTitle(graphics, font, frame, title, tab, controlMode, mouseX, mouseY, opacity);

        Rect area = pageArea(frame);
        panel.layout(area.x0(), area.y0(), area.x1(), area.y1());
        graphics.fill(area.x0(), area.y0(), area.x1(), area.y1(), Theme.fade(Theme.FIELD, opacity));

        if (browser.status() == Browser.Status.READY) {
            if (!panel.render(graphics, tab, opacity)) {
                panel.renderPlaceholder(graphics, font,
                        Component.translatable("pocketbrowser.status.loading"), opacity);
            }
        } else if (browser.status() == Browser.Status.FAILED) {
            graphics.centeredText(font, Component.translatable("pocketbrowser.pin.failed"),
                    area.centreX(), area.centreY() - 4, Theme.fade(Theme.DANGER, opacity));
        } else {
            panel.renderPlaceholder(graphics, font,
                    Component.translatable("pocketbrowser.status.starting"), opacity);
        }

        float visibility = tab == null ? 0.0F
                : mediaVisibility(frame, tab.media(), controlMode, mouseX, mouseY);
        lastMediaVisibility = visibility;
        if (visibility > 0.0F) {
            drawMedia(graphics, font, frame, tab.media(), controlMode, mouseX, mouseY,
                    opacity * visibility);
        }

        if (controlMode) {
            Icons.draw(graphics, Icons.GRIP, frame.x1() - 10, frame.y1() - 10,
                    Theme.fade(Theme.TEXT_FAINT, opacity));
        }
    }

    private static void drawMedia(GuiGraphicsExtractor graphics, Font font, Rect frame,
                                  BrowserTab.Media media, boolean controlMode,
                                  int mouseX, int mouseY, float opacity) {
        Rect bar = mediaBar(frame);
        if (bar.width() < 60) {
            return;
        }
        graphics.fill(bar.x0(), bar.y0(), bar.x1(), bar.y1(), Theme.fade(0xC0000000, opacity));

        Rect button = mediaButton(frame);
        boolean hovered = controlMode && button.contains(mouseX, mouseY);
        Icons.centered(graphics, media.paused() ? Icons.PLAY : Icons.PAUSE,
                button.x0(), button.y0(), button.x1(), button.y1(),
                Theme.fade(hovered ? 0xFFFFFFFF : 0xFFD8DCE4, opacity));

        Rect track = mediaTrack(frame);
        if (track.width() > 8) {
            graphics.fill(track.x0(), track.y0(), track.x1(), track.y1(), Theme.fade(0x60FFFFFF, opacity));
            if (media.duration() > 0.0D) {
                int filled = (int) Math.round(track.width()
                        * Math.clamp(media.position() / media.duration(), 0.0D, 1.0D));
                graphics.fill(track.x0(), track.y0(), track.x0() + filled, track.y1(),
                        Theme.fade(Theme.ACCENT, opacity));
            }
        }

        String time = media.duration() > 0.0D
                ? clock(media.position()) + " / " + clock(media.duration())
                : clock(media.position());
        graphics.text(font, Theme.ellipsize(font, time, 34), bar.x1() - 33, bar.y0() + 3,
                Theme.fade(0xFFB9BFCA, opacity), false);
    }

    private static String clock(double seconds) {
        int total = (int) Math.max(0.0D, seconds);
        int minutes = total / 60;
        return minutes >= 60
                ? String.format("%d:%02d:%02d", minutes / 60, minutes % 60, total % 60)
                : String.format("%d:%02d", minutes, total % 60);
    }

    private static void drawTitle(GuiGraphicsExtractor graphics, Font font, Rect frame, Rect title,
                                  @Nullable BrowserTab tab, boolean controlMode,
                                  int mouseX, int mouseY, float opacity) {
        String url = tab == null ? "" : tab.url();
        boolean secure = Urls.isSecure(url);
        Icons.draw(graphics, secure ? Icons.LOCK : Icons.GLOBE, title.x0() + 3, title.y0() + 2,
                Theme.fade(secure ? Theme.SECURE : Theme.TEXT_FAINT, opacity));

        int textLeft = title.x0() + 14;
        int textRight = controlMode ? expandButton(frame).x0() - 4 : title.x1() - 4;

        String label;
        if (tab == null || Urls.isBlank(url)) {
            label = Component.translatable("pocketbrowser.title").getString();
        } else {
            String host = Urls.host(url);
            String name = tab.title();
            label = name.isBlank() ? host : name;
        }

        int hintWidth = 0;
        if (!controlMode) {
            String hint = Component.translatable("pocketbrowser.pin.hint",
                    PocketBrowserClient.pinFocusKeyName()).getString();
            hintWidth = font.width(hint) + 6;

            if (textRight - textLeft > hintWidth + 40) {
                graphics.text(font, hint, textRight - hintWidth + 4, title.y0() + 3,
                        Theme.fade(Theme.TEXT_FAINT, opacity), false);
            } else {
                hintWidth = 0;
            }
        }

        int room = textRight - textLeft - hintWidth;
        if (room > 8) {
            graphics.text(font, Theme.ellipsize(font, label, room), textLeft, title.y0() + 3,
                    Theme.fade(Theme.TEXT_DIM, opacity), false);
        }

        if (controlMode) {
            drawTitleButton(graphics, expandButton(frame), Icons.PLUS, mouseX, mouseY, opacity, Theme.TEXT_DIM);
            drawTitleButton(graphics, unpinButton(frame), Icons.CROSS, mouseX, mouseY, opacity, Theme.TEXT_DIM);
        }
    }

    private static void drawTitleButton(GuiGraphicsExtractor graphics, Rect rect, String[] icon,
                                        int mouseX, int mouseY, float opacity, int color) {
        boolean hovered = rect.contains(mouseX, mouseY);
        if (hovered) {
            Theme.roundedRect(graphics, rect.x0(), rect.y0(), rect.x1(), rect.y1(),
                    Theme.fade(Theme.HOVER, opacity));
        }
        Icons.centered(graphics, icon, rect.x0(), rect.y0(), rect.x1(), rect.y1(),
                Theme.fade(hovered ? Theme.TEXT : color, opacity));
    }
}
