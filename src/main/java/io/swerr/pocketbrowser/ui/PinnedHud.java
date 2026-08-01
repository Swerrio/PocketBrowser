package io.swerr.pocketbrowser.ui;

import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.ui.screen.BrowserScreen;
import io.swerr.pocketbrowser.ui.screen.PinnedScreen;
import io.swerr.pocketbrowser.ui.widget.PagePanel;
import io.swerr.pocketbrowser.ui.widget.Rect;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import io.swerr.pocketbrowser.util.GameCompatibility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PinnedHud implements HudElement {
    private final PagePanel panel = new PagePanel();

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Config config = Config.get();
        if (!config.pinEnabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (config.pinHideWithHud && GameCompatibility.hudHidden(minecraft)) {
            return;
        }
        if (minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        if (GameCompatibility.screen(minecraft) instanceof BrowserScreen
                || GameCompatibility.screen(minecraft) instanceof PinnedScreen) {
            return;
        }
        if (Browser.get().status() == Browser.Status.IDLE) {
            return;
        }

        Rect frame = PinnedWindow.frame(graphics.guiWidth(), graphics.guiHeight());
        PinnedWindow.draw(graphics, minecraft.font, frame, panel, false, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }
}
