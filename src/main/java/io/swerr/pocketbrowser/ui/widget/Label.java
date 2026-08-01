package io.swerr.pocketbrowser.ui.widget;

import io.swerr.pocketbrowser.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class Label extends AbstractWidget {
    public Label(Component message) {
        super(0, 0, 0, 0, message);
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        Component text = getMessage().copy()
                .withColor((Theme.isVanilla() ? 0xFFFFFF : Theme.TEXT) & 0xFFFFFF);
        int fits = Math.min(getWidth(), Minecraft.getInstance().font.width(text));
        graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                .acceptScrolling(text, getX() + fits / 2, getX(), getX() + getWidth(),
                        getY(), getY() + getHeight());
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
