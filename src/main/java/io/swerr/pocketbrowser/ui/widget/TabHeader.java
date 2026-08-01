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

public class TabHeader extends AbstractWidget {
    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/tab_selected"),
            Identifier.withDefaultNamespace("widget/tab"),
            Identifier.withDefaultNamespace("widget/tab_selected_highlighted"),
            Identifier.withDefaultNamespace("widget/tab_highlighted"));

    private final Runnable select;
    private boolean selected;

    public TabHeader(int x, int y, int width, int height, Component message, Runnable select) {
        super(x, y, width, height, message);
        this.select = select;
    }

    public void setSelected(boolean value) {
        this.selected = value;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean lit = isHoveredOrFocused();
        int x1 = getX() + getWidth();
        int y1 = getY() + getHeight();
        int colour;

        if (Theme.isVanilla()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(selected, lit),
                    getX(), getY(), getWidth(), getHeight());
            colour = selected ? 0xFFFFFFFF : 0xFFA0A0A0;
        } else {
            Theme.roundedRect(graphics, getX(), getY(), x1, y1,
                    selected ? Theme.CHROME_HIGH : (lit ? Theme.CHROME : Theme.WINDOW));
            Theme.roundedOutline(graphics, getX(), getY(), x1, y1,
                    selected ? Theme.ACCENT : Theme.BORDER_SOFT);
            if (selected) {
                graphics.fill(getX() + 2, y1 - 2, x1 - 2, y1, Theme.ACCENT);
            }
            colour = selected ? Theme.TEXT : Theme.TEXT_DIM;
        }

        Component text = getMessage().copy().withColor(colour & 0xFFFFFF);
        graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR)
                .acceptScrolling(text, getX() + getWidth() / 2, getX() + 3, x1 - 3, getY(), y1);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        select.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
