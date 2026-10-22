package io.swerr.pocketbrowser.ui.screen;

import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.engine.BrowserTab;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.engine.cdp.CdpKeys;
import io.swerr.pocketbrowser.ui.PinnedWindow;
import io.swerr.pocketbrowser.ui.theme.Theme;
import io.swerr.pocketbrowser.ui.widget.PagePanel;
import io.swerr.pocketbrowser.ui.widget.Rect;
import io.swerr.pocketbrowser.util.GameCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;

public class PinnedScreen extends Screen {
    private enum Drag {
        NONE, MOVE, RESIZE
    }

    private final PagePanel panel = new PagePanel();
    private Drag drag = Drag.NONE;

    private double grabX;
    private double grabY;

    public PinnedScreen() {
        super(Component.translatable("pocketbrowser.pin.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        Browser.get().start();
        minecraft.textInputManager().startTextInput(this);
    }

    @Override
    public void tick() {
        Browser.get().touch();
    }

    @Override
    public void removed() {
        Config.get().save();
        panel.cancelDrag();
        minecraft.textInputManager().stopTextInput(this);
    }

    private Rect frame() {
        return PinnedWindow.frame(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Rect frame = frame();
        PinnedWindow.draw(graphics, font, frame, panel, true, mouseX, mouseY);

        graphics.centeredText(font, Component.translatable("pocketbrowser.pin.controls"),
                width / 2, height - 12, Theme.fade(Theme.TEXT_FAINT, 0.9F));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        Rect frame = frame();

        if (PinnedWindow.expandButton(frame).contains(mouseX, mouseY)) {
            openFullBrowser(frame);
            return true;
        }
        if (PinnedWindow.unpinButton(frame).contains(mouseX, mouseY)) {
            Config config = Config.get();
            config.pinEnabled = false;
            config.save();
            onClose();
            return true;
        }
        if (PinnedWindow.gripArea(frame).contains(mouseX, mouseY)) {
            drag = Drag.RESIZE;
            grabX = mouseX - frame.x1();
            grabY = mouseY - frame.y1();
            return true;
        }
        if (PinnedWindow.titleBar(frame).contains(mouseX, mouseY)) {
            drag = Drag.MOVE;
            grabX = mouseX - frame.x0();
            grabY = mouseY - frame.y0();
            return true;
        }

        BrowserTab playing = Browser.get().activeTab();
        if (playing != null && PinnedWindow.mediaBarHit(frame, mouseX, mouseY)) {
            if (PinnedWindow.mediaButton(frame).contains(mouseX, mouseY)) {
                playing.mediaTogglePlay();
            } else {
                Rect track = PinnedWindow.mediaTrack(frame);
                if (track.width() > 8 && mouseX >= track.x0() && mouseX <= track.x1()) {
                    playing.mediaSeekTo((mouseX - track.x0()) / (double) track.width());
                }
            }
            return true;
        }

        if (PinnedWindow.pageArea(frame).contains(mouseX, mouseY)) {
            int modifiers = CdpKeys.modifiers(event.modifiers());
            boolean ctrlClick = event.button() == SDLMouse.SDL_BUTTON_LEFT && (modifiers & 2) != 0;
            if (event.button() == SDLMouse.SDL_BUTTON_MIDDLE || ctrlClick) {
                panel.openLinkUnder(Browser.get().activeTab(), mouseX, mouseY, ctrlClick, modifiers);
                return true;
            }
            panel.mousePressed(Browser.get().activeTab(), mouseX, mouseY, event.button(),
                    doubleClick ? 2 : 1, modifiers);
            return true;
        }

        onClose();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Config config = Config.get();
        switch (drag) {
            case MOVE -> {
                Rect frame = frame();
                config.pinX = (int) Math.round(event.x() - grabX);
                config.pinY = (int) Math.round(event.y() - grabY);
                config.pinX = Math.clamp(config.pinX, 2, Math.max(2, width - frame.width() - 2));
                config.pinY = Math.clamp(config.pinY, 2, Math.max(2, height - frame.height() - 2));
                return true;
            }
            case RESIZE -> {
                Rect frame = frame();
                config.pinWidth = (int) Math.round(event.x() - grabX) - frame.x0();
                config.pinHeight = (int) Math.round(event.y() - grabY) - frame.y0();
                config.pinWidth = Math.clamp(config.pinWidth, 120, Math.max(120, width - frame.x0() - 2));
                config.pinHeight = Math.clamp(config.pinHeight, 80, Math.max(80, height - frame.y0() - 2));
                return true;
            }
            default -> {
                if (panel.isDragging()) {
                    panel.mouseMoved(Browser.get().activeTab(), event.x(), event.y(),
                            CdpKeys.modifiers(event.modifiers()));
                    return true;
                }
                return super.mouseDragged(event, dragX, dragY);
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            Config.get().save();
            return true;
        }
        if (panel.isDragging()) {
            panel.mouseReleased(Browser.get().activeTab(), event.x(), event.y(),
                    event.button(), CdpKeys.modifiers(event.modifiers()));
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        PinnedWindow.nudgeMedia();
        if (drag == Drag.NONE && !panel.isDragging() && panel.contains(mouseX, mouseY)) {
            panel.mouseMoved(Browser.get().activeTab(), mouseX, mouseY, 0);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panel.contains(mouseX, mouseY)) {
            panel.scroll(Browser.get().activeTab(), mouseX, mouseY, scrollX, scrollY, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == SDLScancode.SDL_SCANCODE_ESCAPE) {
            onClose();
            return true;
        }
        BrowserTab tab = Browser.get().activeTab();
        if (tab == null) {
            return super.keyPressed(event);
        }
        if (event.isPaste()) {
            tab.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (event.isCopy()) {
            tab.selection().thenAccept(text -> {
                if (!text.isEmpty() && minecraft != null) {
                    minecraft.execute(() -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
                }
            });
            return true;
        }
        CdpKeys.Key key = CdpKeys.of(event.key());
        if (key != null) {
            tab.keyDown(key, CdpKeys.modifiers(event.modifiers()));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        BrowserTab tab = Browser.get().activeTab();
        CdpKeys.Key key = CdpKeys.of(event.key());
        if (tab != null && key != null) {
            tab.keyUp(key, CdpKeys.modifiers(event.modifiers()));
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        BrowserTab tab = Browser.get().activeTab();
        if (tab != null) {
            tab.typed(event.codepointAsString());
            return true;
        }
        return super.charTyped(event);
    }

    private void openFullBrowser(Rect frame) {
        if (minecraft != null) {
            GameCompatibility.openScreen(minecraft, new BrowserScreen(null, frame.centreX(), frame.centreY()));
        }
    }

    @Override
    public void onClose() {
        closeTo(null);
    }

    private void closeTo(@Nullable Screen next) {
        if (minecraft != null) {
            GameCompatibility.openScreen(minecraft, next);
        }
    }
}
