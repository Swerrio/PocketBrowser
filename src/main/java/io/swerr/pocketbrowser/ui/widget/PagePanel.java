package io.swerr.pocketbrowser.ui.widget;

import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.engine.BrowserTab;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.engine.cdp.CdpKeys;
import io.swerr.pocketbrowser.engine.Viewport;
import io.swerr.pocketbrowser.render.PageTexture;
import io.swerr.pocketbrowser.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

public final class PagePanel {
    private int x0;
    private int y0;
    private int x1;
    private int y1;

    private boolean dragging;
    private int draggingButton = -1;

    public void layout(int left, int top, int right, int bottom) {
        this.x0 = left;
        this.y0 = top;
        this.x1 = Math.max(left + 1, right);
        this.y1 = Math.max(top + 1, bottom);
    }

    public int left() {
        return x0;
    }

    public int top() {
        return y0;
    }

    public int right() {
        return x1;
    }

    public int bottom() {
        return y1;
    }

    public int width() {
        return x1 - x0;
    }

    public int height() {
        return y1 - y0;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
    }

    public boolean render(GuiGraphicsExtractor graphics, @Nullable BrowserTab tab, float opacity) {
        if (tab == null || tab.isClosed()) {
            return false;
        }

        Browser.get().noteDrawn();

        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        Config config = Config.get();
        tab.applyViewport(Viewport.of(width(), height(), guiScale, config.zoom, config.maxFrameWidth));

        PageTexture texture = tab.texture();
        if (!texture.sync()) {
            return false;
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.name(),
                x0, y0, 0.0F, 0.0F,
                width(), height(),
                texture.width(), texture.height(),
                texture.width(), texture.height(),
                ARGB.white(Math.clamp(opacity, 0.0F, 1.0F)));
        return true;
    }

    public void renderPlaceholder(GuiGraphicsExtractor graphics, Font font, Component message, float opacity) {
        graphics.fill(x0, y0, x1, y1, Theme.fade(Theme.FIELD, opacity));
        int centreX = (x0 + x1) / 2;
        int centreY = (y0 + y1) / 2;
        Spinner.draw(graphics, centreX, centreY - 8, Theme.fade(Theme.ACCENT, opacity * 0.9F));
        graphics.centeredText(font, message, centreX, centreY + 6, Theme.fade(Theme.TEXT_DIM, opacity));
    }

    private double cssX(BrowserTab tab, double mouseX) {
        Viewport viewport = tab.viewport();
        if (viewport == null) {
            return mouseX - x0;
        }
        double scale = (double) viewport.frameWidth() / Math.max(1, width());
        return Math.clamp((mouseX - x0) * scale, 0.0D, viewport.frameWidth() - 1.0D);
    }

    private double cssY(BrowserTab tab, double mouseY) {
        Viewport viewport = tab.viewport();
        if (viewport == null) {
            return mouseY - y0;
        }
        double scale = (double) viewport.frameHeight() / Math.max(1, height());
        return Math.clamp((mouseY - y0) * scale, 0.0D, viewport.frameHeight() - 1.0D);
    }

    public void openLinkUnder(@Nullable BrowserTab tab, double mouseX, double mouseY,
                              boolean forwardWhenNoLink, int modifiers) {
        if (tab == null) {
            return;
        }
        double x = cssX(tab, mouseX);
        double y = cssY(tab, mouseY);
        tab.linkAt(x, y).thenAccept(href -> {
            if (!href.isBlank()) {
                Minecraft.getInstance().execute(() -> Browser.get().newTab(href, false));
            } else if (forwardWhenNoLink) {
                tab.mousePressed(x, y, 0, 1, modifiers);
                tab.mouseReleased(x, y, 0, 1, modifiers);
            }
        });
    }

    public void mouseMoved(@Nullable BrowserTab tab, double mouseX, double mouseY, int modifiers) {
        if (tab == null) {
            return;
        }
        int held = dragging ? CdpKeys.buttonMask(draggingButton) : 0;
        tab.mouseMoved(cssX(tab, mouseX), cssY(tab, mouseY), held, modifiers);
    }

    public void mousePressed(@Nullable BrowserTab tab, double mouseX, double mouseY,
                             int button, int clickCount, int modifiers) {
        if (tab == null) {
            return;
        }
        dragging = true;
        draggingButton = button;

        tab.mouseMoved(cssX(tab, mouseX), cssY(tab, mouseY), 0, modifiers);
        tab.mousePressed(cssX(tab, mouseX), cssY(tab, mouseY), button, Math.max(1, clickCount), modifiers);
    }

    public void mouseReleased(@Nullable BrowserTab tab, double mouseX, double mouseY,
                              int button, int modifiers) {
        dragging = false;
        draggingButton = -1;
        if (tab != null) {
            tab.mouseReleased(cssX(tab, mouseX), cssY(tab, mouseY), button, 1, modifiers);
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    public void cancelDrag() {
        dragging = false;
        draggingButton = -1;
    }

    public void scroll(@Nullable BrowserTab tab, double mouseX, double mouseY,
                       double scrollX, double scrollY, int modifiers) {
        if (tab == null) {
            return;
        }
        tab.scroll(cssX(tab, mouseX), cssY(tab, mouseY), scrollX * -80.0D, scrollY * -80.0D, modifiers);
    }
}
