package io.swerr.pocketbrowser.ui.widget;

import io.swerr.pocketbrowser.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class ThemedButton extends AbstractWidget {
    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"),
            Identifier.withDefaultNamespace("widget/button_highlighted"));

    @Nullable
    private final Runnable action;

    public ThemedButton(int x, int y, int width, int height, Component message, @Nullable Runnable action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean lit = isHoveredOrFocused();
        int x1 = getX() + getWidth();
        int y1 = getY() + getHeight();
        int colour;

        if (Theme.isVanilla()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(active, lit),
                    getX(), getY(), getWidth(), getHeight());
            colour = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        } else {
            Theme.roundedRect(graphics, getX(), getY(), x1, y1, lit ? Theme.CHROME_HIGH : Theme.CHROME);
            Theme.roundedOutline(graphics, getX(), getY(), x1, y1, lit ? Theme.ACCENT : Theme.BORDER);
            colour = active ? Theme.TEXT : Theme.TEXT_FAINT;
        }

        Component text = getMessage().copy().withColor(colour & 0xFFFFFF);
        graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR)
                .acceptScrolling(text, getX() + getWidth() / 2, getX() + 4, x1 - 4, getY(), y1);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    protected void press() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
