package io.swerr.pocketbrowser.ui.screen;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.data.BrowsingData;
import io.swerr.pocketbrowser.engine.BrowserTab;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.engine.cdp.CdpKeys;
import io.swerr.pocketbrowser.ui.theme.Theme;
import io.swerr.pocketbrowser.ui.widget.Easing;
import io.swerr.pocketbrowser.ui.widget.Icons;
import io.swerr.pocketbrowser.ui.widget.PagePanel;
import io.swerr.pocketbrowser.ui.widget.Rect;
import io.swerr.pocketbrowser.ui.widget.Spinner;
import io.swerr.pocketbrowser.ui.widget.UrlBar;
import io.swerr.pocketbrowser.util.Urls;
import io.swerr.pocketbrowser.util.GameCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class BrowserScreen extends Screen {
    private static final long OPEN_NANOS = 240_000_000L;
    private static final long CLOSE_NANOS = 150_000_000L;

    private static final int TAB_STRIP_HEIGHT = 22;
    private static final int TOOLBAR_HEIGHT = 26;
    private static final int FIND_HEIGHT = 22;
    private static final int BUTTON = 20;
    private static final int MAX_TABS = Browser.MAX_TABS;
    private static final int SUGGESTION_HEIGHT = 15;
    private static final int MAX_SUGGESTIONS = 7;
    private static final int MENU_WIDTH = 208;
    private static final int MENU_ROW = 16;
    private static final int MENU_STEP = 18;
    private static final int MENU_VALUE = 46;

    private static final int MIN_WINDOW_WIDTH = 260;
    private static final int MIN_WINDOW_HEIGHT = 170;
    private static final int GRIP = 12;

    private enum Drag {
        NONE, MOVE, RESIZE
    }

    @Nullable
    private final Screen parent;
    private final double summonX;
    private final double summonY;

    private final PagePanel page = new PagePanel();
    private final UrlBar omnibox = new UrlBar();
    private final UrlBar finder = new UrlBar();

    private long openedAtNanos;
    private boolean closing;
    private long closingAtNanos;

    private boolean findVisible;
    private boolean menuOpen;
    private int highlightedSuggestion = -1;
    private List<BrowsingData.Entry> suggestions = List.of();
    private String lastRecordedUrl = "";

    private int winX0;
    private int winY0;
    private int winX1;
    private int winY1;
    private int tabsY0;
    private int tabsY1;
    private int barY0;
    private int barY1;
    private int tabsAreaX0;
    private int tabsAreaX1;

    private int tabLimit;
    private int tabWidth;
    private int suggestionTop;

    private Rect back = Rect.NONE;
    private Rect forward = Rect.NONE;
    private Rect reload = Rect.NONE;
    private Rect home = Rect.NONE;
    private Rect omniboxRect = Rect.NONE;
    private Rect star = Rect.NONE;
    private Rect pin = Rect.NONE;
    private Rect menu = Rect.NONE;
    private Rect newTab = Rect.NONE;
    private Rect closeWindow = Rect.NONE;
    private Rect maximizeWindow = Rect.NONE;

    private Drag drag = Drag.NONE;
    private double grabX;
    private double grabY;
    private Rect findPrev = Rect.NONE;
    private Rect findNext = Rect.NONE;
    private Rect findClose = Rect.NONE;
    private Rect findFieldRect = Rect.NONE;
    private Rect retry = Rect.NONE;

    public BrowserScreen(@Nullable Screen parent) {
        this(parent, -1.0D, -1.0D);
    }

    public BrowserScreen(@Nullable Screen parent, double summonX, double summonY) {
        super(Component.translatable("pocketbrowser.title"));
        this.parent = parent;
        this.summonX = summonX;
        this.summonY = summonY;
    }

    @Override
    protected void init() {
        if (openedAtNanos == 0L) {
            openedAtNanos = System.nanoTime();
        }
        Browser.get().start();
        layout();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        Browser browser = Browser.get();
        browser.touch();

        if (!browser.signInRunning() && browser.status() == Browser.Status.IDLE) {
            browser.start();
        }

        BrowserTab tab = browser.activeTab();
        if (tab != null && !omnibox.isFocused()) {
            omnibox.setText(Urls.isBlank(tab.url()) ? "" : tab.url());
        }
        if (tab != null && !tab.loading()) {
            String url = tab.url();
            if (!url.equals(lastRecordedUrl) && !Urls.isBlank(url)) {
                lastRecordedUrl = url;
                BrowsingData.recordVisit(url, tab.title());
            }
        }
    }

    @Override
    public void removed() {
        Browser.get().rememberLastPage();
        page.cancelDrag();
    }

    @Override
    public void onClose() {
        beginClose();
    }

    private void beginClose() {
        if (!closing) {
            closing = true;
            closingAtNanos = System.nanoTime();
        }
    }

    private void finishClose() {
        if (minecraft != null) {
            GameCompatibility.openScreen(minecraft, parent);
        }
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private boolean controlDown() {
        if (minecraft == null) {
            return false;
        }
        return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private void layout() {
        Config config = Config.get();
        if (config.winMaximized || config.winWidth < MIN_WINDOW_WIDTH || config.winHeight < MIN_WINDOW_HEIGHT) {
            layoutMaximized();
        } else {
            int windowWidth = Math.clamp(config.winWidth, MIN_WINDOW_WIDTH, Math.max(MIN_WINDOW_WIDTH, width - 4));
            int windowHeight = Math.clamp(config.winHeight, MIN_WINDOW_HEIGHT,
                    Math.max(MIN_WINDOW_HEIGHT, height - 4));
            winX0 = Math.clamp(config.winX, 2, Math.max(2, width - windowWidth - 2));
            winY0 = Math.clamp(config.winY, 2, Math.max(2, height - windowHeight - 2));
            winX1 = winX0 + windowWidth;
            winY1 = winY0 + windowHeight;
        }

        tabsY0 = winY0 + 1;
        tabsY1 = tabsY0 + TAB_STRIP_HEIGHT;
        barY0 = tabsY1;
        barY1 = barY0 + TOOLBAR_HEIGHT;

        layoutTabStrip();
        layoutToolbar();

        int pageTop = barY1 + 2;
        if (findVisible) {
            layoutFindBar(pageTop);
            pageTop += FIND_HEIGHT;
        } else {
            findFieldRect = Rect.NONE;
            findPrev = Rect.NONE;
            findNext = Rect.NONE;
            findClose = Rect.NONE;
        }

        suggestionTop = pageTop + 1;
        page.layout(winX0 + 1, pageTop, winX1 - 1, winY1 - 1);

        retry = Rect.NONE;
    }

    private void layoutMaximized() {
        int horizontal = Math.clamp(Math.round(width * 0.045F), 4, 46);
        int vertical = Math.clamp(Math.round(height * 0.055F), 4, 40);
        winX0 = horizontal;
        winX1 = width - horizontal;
        winY0 = vertical;
        winY1 = height - vertical;

        if (winX1 - winX0 < 220) {
            winX0 = 2;
            winX1 = Math.max(winX0 + 40, width - 2);
        }
        if (winY1 - winY0 < 150) {
            winY0 = 2;
            winY1 = Math.max(winY0 + 40, height - 2);
        }
    }

    private void layoutTabStrip() {
        int iconTop = tabsY0 + (TAB_STRIP_HEIGHT - 16) / 2;
        closeWindow = new Rect(winX1 - 4 - 16, iconTop, winX1 - 4, iconTop + 16);
        maximizeWindow = new Rect(closeWindow.x0() - 18, iconTop, closeWindow.x0() - 2, iconTop + 16);

        tabsAreaX0 = winX0 + 4;
        tabsAreaX1 = maximizeWindow.x0() - 6;

        tabLimit = Math.max(tabsAreaX0, tabsAreaX1 - 18);

        int count = Math.max(1, Browser.get().tabs().size());
        int room = Math.max(0, tabLimit - tabsAreaX0);
        tabWidth = Math.clamp(room / count, 26, 168);

        int plusX = Math.min(tabsAreaX0 + count * tabWidth + 2, tabsAreaX1 - 16);
        newTab = new Rect(plusX, iconTop, plusX + 16, iconTop + 16);
    }

    private void layoutToolbar() {
        int windowWidth = winX1 - winX0;
        boolean showHome = windowWidth >= 320;
        boolean showStar = windowWidth >= 270;

        int y = barY0 + (TOOLBAR_HEIGHT - BUTTON) / 2;
        int cursor = winX0 + 4;

        back = new Rect(cursor, y, cursor + BUTTON, y + BUTTON);
        cursor += BUTTON;
        forward = new Rect(cursor, y, cursor + BUTTON, y + BUTTON);
        cursor += BUTTON;
        reload = new Rect(cursor, y, cursor + BUTTON, y + BUTTON);
        cursor += BUTTON;
        if (showHome) {
            home = new Rect(cursor, y, cursor + BUTTON, y + BUTTON);
            cursor += BUTTON;
        } else {
            home = Rect.NONE;
        }

        int rightEdge = winX1 - 4;
        menu = new Rect(rightEdge - BUTTON, y, rightEdge, y + BUTTON);
        pin = new Rect(menu.x0() - BUTTON, y, menu.x0(), y + BUTTON);
        if (showStar) {
            star = new Rect(pin.x0() - BUTTON, y, pin.x0(), y + BUTTON);
        } else {
            star = Rect.NONE;
        }

        int fieldLeft = cursor + 5;
        int fieldRight = (showStar ? star.x0() : pin.x0()) - 5;
        omniboxRect = fieldRight - fieldLeft < 30
                ? Rect.NONE
                : new Rect(fieldLeft, y + 1, fieldRight, y + BUTTON - 1);
    }

    private void layoutFindBar(int top) {
        int y = top + 2;
        int height = FIND_HEIGHT - 4;
        int rightEdge = winX1 - 6;
        findClose = new Rect(rightEdge - 16, y, rightEdge, y + height);
        findNext = new Rect(findClose.x0() - 18, y, findClose.x0() - 2, y + height);
        findPrev = new Rect(findNext.x0() - 18, y, findNext.x0() - 2, y + height);
        findFieldRect = new Rect(winX0 + 6, y, Math.max(winX0 + 40, findPrev.x0() - 6), y + height);
    }

    private float progress() {
        long now = System.nanoTime();
        if (closing) {
            float t = (now - closingAtNanos) / (float) CLOSE_NANOS;
            return 1.0F - Math.clamp(t, 0.0F, 1.0F);
        }
        return Math.clamp((now - openedAtNanos) / (float) OPEN_NANOS, 0.0F, 1.0F);
    }

    private boolean animating() {
        float t = progress();
        return closing || t < 1.0F;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float raw = progress();
        if (closing && raw <= 0.0F) {
            finishClose();
            return;
        }

        layoutTabStrip();

        float eased = closing ? Easing.inCubic(raw) : Easing.outCubic(raw);
        float opacity = Math.clamp(closing ? raw * 1.4F : raw * 1.5F, 0.0F, 1.0F);
        float scale = Easing.lerp(0.93F, 1.0F, eased);

        graphics.fill(0, 0, width, height, Theme.fade(Theme.SCRIM, eased));

        float originX = summonX >= 0.0D ? (float) summonX : width / 2.0F;
        float originY = summonY >= 0.0D ? (float) summonY : height / 2.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-originX, -originY);

        Theme.window(graphics, winX0, winY0, winX1, winY1, opacity);
        graphics.fill(winX0 + 1, winY0 + 1, winX1 - 1, barY1, Theme.fade(Theme.CHROME, opacity));

        int hoverX = animating() ? Integer.MIN_VALUE : mouseX;
        int hoverY = animating() ? Integer.MIN_VALUE : mouseY;

        renderTabStrip(graphics, hoverX, hoverY, opacity);
        renderToolbar(graphics, hoverX, hoverY, opacity);
        Theme.separator(graphics, winX0 + 1, winX1 - 1, barY1, opacity);
        renderLoadingBar(graphics, opacity);

        if (findVisible) {
            renderFindBar(graphics, hoverX, hoverY, opacity);
        }

        renderContent(graphics, hoverX, hoverY, opacity);
        renderSuggestions(graphics, hoverX, hoverY, opacity);
        renderMenu(graphics, hoverX, hoverY, opacity);

        if (!Config.get().winMaximized) {
            Rect grip = resizeGrip();
            Icons.draw(graphics, Icons.GRIP, grip.x0() + 2, grip.y0() + 2,
                    Theme.fade(grip.contains(hoverX, hoverY) ? Theme.TEXT : Theme.TEXT_FAINT, opacity));
        }

        graphics.pose().popMatrix();

        if (!animating()) {
            renderTooltips(graphics, mouseX, mouseY);
        }
    }

    private void renderTabStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        Browser browser = Browser.get();
        List<BrowserTab> tabs = browser.tabs();
        int active = browser.activeIndex();

        for (int i = 0; i < tabs.size(); i++) {
            int x0 = tabsAreaX0 + i * tabWidth;
            int x1 = x0 + tabWidth - 2;
            if (x1 > tabLimit) {
                break;
            }
            BrowserTab tab = tabs.get(i);
            boolean isActive = i == active;
            Rect bounds = new Rect(x0, tabsY0 + 2, x1, tabsY1);
            boolean hovered = bounds.contains(mouseX, mouseY);

            int background = isActive ? Theme.CHROME_HIGH : (hovered ? Theme.mix(Theme.CHROME, Theme.CHROME_HIGH, 0.6F)
                    : Theme.CHROME);
            Theme.roundedRect(graphics, x0, tabsY0 + 2, x1, tabsY1, Theme.fade(background, opacity));
            if (isActive) {
                graphics.fill(x0 + 2, tabsY1 - 2, x1 - 2, tabsY1, Theme.fade(Theme.ACCENT, opacity));
            }

            int dotColor = tab.loading()
                    ? Theme.ACCENT
                    : (Urls.isSecure(tab.url()) ? Theme.SECURE : Theme.TEXT_FAINT);
            if (tab.loading()) {
                float pulse = 0.45F + 0.55F * (float) Math.abs(Math.sin(System.currentTimeMillis() / 260.0D));
                dotColor = Theme.fade(dotColor, pulse);
            }
            int dotY = (tabsY0 + 2 + tabsY1) / 2;
            graphics.fill(x0 + 6, dotY - 2, x0 + 10, dotY + 2, Theme.fade(dotColor, opacity));

            boolean showClose = tabWidth >= 52 && (isActive || hovered);
            int textRight = x1 - (showClose ? 16 : 5);
            int textLeft = x0 + 14;
            if (textRight > textLeft) {
                String label = tab.title().isBlank()
                        ? Component.translatable("pocketbrowser.tab.untitled").getString()
                        : tab.title();
                String fitted = Theme.ellipsize(font, label, textRight - textLeft);
                if (!fitted.isEmpty()) {
                    graphics.text(font, fitted, textLeft, dotY - font.lineHeight / 2,
                            Theme.fade(isActive ? Theme.TEXT : Theme.TEXT_DIM, opacity), false);
                }
            }

            if (showClose) {
                Rect closeRect = new Rect(x1 - 15, dotY - 7, x1 - 1, dotY + 7);
                boolean closeHovered = closeRect.contains(mouseX, mouseY);
                if (closeHovered) {
                    Theme.roundedRect(graphics, closeRect.x0(), closeRect.y0(), closeRect.x1(), closeRect.y1(),
                            Theme.fade(Theme.HOVER, opacity));
                }
                Icons.centered(graphics, Icons.CROSS, closeRect.x0(), closeRect.y0(),
                        closeRect.x1(), closeRect.y1(),
                        Theme.fade(closeHovered ? Theme.TEXT : Theme.TEXT_FAINT, opacity));
            }
        }

        boolean canAddTab = tabs.size() < MAX_TABS;
        iconButton(graphics, newTab, Icons.PLUS, canAddTab, mouseX, mouseY, opacity, Theme.TEXT_DIM);
        iconButton(graphics, maximizeWindow,
                Config.get().winMaximized ? Icons.RESTORE : Icons.MAXIMIZE,
                true, mouseX, mouseY, opacity, Theme.TEXT_DIM);
        iconButton(graphics, closeWindow, Icons.CROSS, true, mouseX, mouseY, opacity, Theme.TEXT_DIM);
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        BrowserTab tab = Browser.get().activeTab();
        boolean ready = tab != null;

        iconButton(graphics, back, Icons.BACK, ready && tab.canGoBack(), mouseX, mouseY, opacity, Theme.TEXT);
        iconButton(graphics, forward, Icons.FORWARD, ready && tab.canGoForward(), mouseX, mouseY, opacity, Theme.TEXT);
        boolean busy = ready && tab.loading();
        iconButton(graphics, reload, busy ? Icons.CROSS : Icons.RELOAD, ready, mouseX, mouseY, opacity, Theme.TEXT);
        if (!home.isEmpty()) {
            iconButton(graphics, home, Icons.HOME, ready, mouseX, mouseY, opacity, Theme.TEXT);
        }

        if (!star.isEmpty()) {
            boolean marked = ready && BrowsingData.isBookmarked(tab.url());
            iconButton(graphics, star, Icons.STAR, ready, mouseX, mouseY, opacity,
                    marked ? Theme.PINNED : Theme.TEXT_FAINT);
        }
        boolean pinned = Config.get().pinEnabled;
        iconButton(graphics, pin, Icons.PIN, true, mouseX, mouseY, opacity,
                pinned ? Theme.PINNED : Theme.TEXT_DIM);
        iconButton(graphics, menu, Icons.MENU, true, mouseX, mouseY, opacity, Theme.TEXT_DIM);

        renderOmnibox(graphics, tab, opacity);
    }

    private void renderOmnibox(GuiGraphicsExtractor graphics, @Nullable BrowserTab tab, float opacity) {
        if (omniboxRect.isEmpty()) {
            return;
        }
        int x0 = omniboxRect.x0();
        int y0 = omniboxRect.y0();
        int x1 = omniboxRect.x1();
        int y1 = omniboxRect.y1();

        Theme.inset(graphics, x0, y0, x1, y1, omnibox.isFocused(), opacity);

        int textLeft = x0 + 4;
        String url = tab == null ? "" : tab.url();
        if (!omnibox.isFocused() && !Urls.isBlank(url)) {
            boolean secure = Urls.isSecure(url);
            Icons.draw(graphics, secure ? Icons.LOCK : Icons.GLOBE, x0 + 5, (y0 + y1) / 2 - 4,
                    Theme.fade(secure ? Theme.SECURE : Theme.TEXT_FAINT, opacity));
            textLeft = x0 + 17;
        }

        if (omnibox.isFocused()) {
            omnibox.render(graphics, font, textLeft - 4, y0, x1, y1, opacity,
                    Component.translatable("pocketbrowser.omnibox.hint").getString());
            return;
        }

        int room = x1 - 5 - textLeft;
        int baseline = (y0 + y1) / 2 - font.lineHeight / 2 + 1;
        if (Urls.isBlank(url)) {
            graphics.text(font, Theme.ellipsize(font,
                            Component.translatable("pocketbrowser.omnibox.hint").getString(), room),
                    textLeft, baseline, Theme.fade(Theme.TEXT_FAINT, opacity), false);
            return;
        }

        String host = Urls.host(url);
        int hostStart = url.indexOf(host);
        if (hostStart < 0) {
            graphics.text(font, Theme.ellipsize(font, url, room), textLeft, baseline,
                    Theme.fade(Theme.TEXT, opacity), false);
            return;
        }
        String prefix = url.substring(0, hostStart);
        String rest = url.substring(hostStart + host.length());
        int x = textLeft;
        x = drawClipped(graphics, prefix, x, baseline, x1 - 5, Theme.fade(Theme.TEXT_FAINT, opacity));
        x = drawClipped(graphics, host, x, baseline, x1 - 5, Theme.fade(Theme.TEXT, opacity));
        drawClipped(graphics, rest, x, baseline, x1 - 5, Theme.fade(Theme.TEXT_FAINT, opacity));
    }

    private int drawClipped(GuiGraphicsExtractor graphics, String text, int x, int y, int limit, int color) {
        if (text.isEmpty() || x >= limit) {
            return x;
        }
        String fitted = Theme.ellipsize(font, text, limit - x);
        if (fitted.isEmpty()) {
            return limit;
        }
        graphics.text(font, fitted, x, y, color, false);
        return x + font.width(fitted);
    }

    private void renderLoadingBar(GuiGraphicsExtractor graphics, float opacity) {
        BrowserTab tab = Browser.get().activeTab();
        if (tab == null || !tab.loading()) {
            return;
        }
        Spinner.progressBar(graphics, winX0 + 1, barY1, winX1 - 1, barY1 + 2, Theme.ACCENT, opacity);
    }

    private void renderFindBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        graphics.fill(winX0 + 1, findFieldRect.y0() - 2, winX1 - 1, findFieldRect.y1() + 2,
                Theme.fade(Theme.CHROME, opacity));
        Theme.inset(graphics, findFieldRect.x0(), findFieldRect.y0(),
                findFieldRect.x1(), findFieldRect.y1(), finder.isFocused(), opacity);
        Icons.draw(graphics, Icons.SEARCH, findFieldRect.x0() + 5, findFieldRect.centreY() - 4,
                Theme.fade(Theme.TEXT_FAINT, opacity));
        finder.render(graphics, font, findFieldRect.x0() + 12, findFieldRect.y0(),
                findFieldRect.x1(), findFieldRect.y1(), opacity,
                Component.translatable("pocketbrowser.find.hint").getString());

        iconButton(graphics, findPrev, Icons.BACK, true, mouseX, mouseY, opacity, Theme.TEXT_DIM);
        iconButton(graphics, findNext, Icons.FORWARD, true, mouseX, mouseY, opacity, Theme.TEXT_DIM);
        iconButton(graphics, findClose, Icons.CROSS, true, mouseX, mouseY, opacity, Theme.TEXT_DIM);
        Theme.separator(graphics, winX0 + 1, winX1 - 1, findFieldRect.y1() + 2, opacity);
    }

    private void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        Browser browser = Browser.get();
        graphics.fill(page.left(), page.top(), page.right(), page.bottom(), Theme.fade(Theme.WINDOW, opacity));

        if (browser.signInRunning()) {
            renderSignIn(graphics, opacity);
            return;
        }

        switch (browser.status()) {
            case READY -> {
                BrowserTab tab = browser.activeTab();
                if (!page.render(graphics, tab, opacity)) {
                    page.renderPlaceholder(graphics, font,
                            Component.translatable("pocketbrowser.status.loading"), opacity);
                }
            }
            case STARTING -> page.renderPlaceholder(graphics, font,
                    Component.translatable("pocketbrowser.status.starting"), opacity);
            case FAILED -> renderFailure(graphics, mouseX, mouseY, opacity);
            case IDLE -> page.renderPlaceholder(graphics, font,
                    Component.translatable("pocketbrowser.status.starting"), opacity);
        }
    }

    private void renderSignIn(GuiGraphicsExtractor graphics, float opacity) {
        int centreX = page.left() + page.width() / 2;
        int y = page.top() + page.height() / 2 - 28;
        Spinner.draw(graphics, centreX, y, Theme.fade(Theme.ACCENT, opacity));
        y += 18;
        graphics.centeredText(font, Component.translatable("pocketbrowser.signin.waiting"),
                centreX, y, Theme.fade(Theme.TEXT, opacity));
        y += 14;
        for (FormattedCharSequence line : font.split(Component.translatable("pocketbrowser.signin.hint"),
                Math.min(340, Math.max(80, page.width() - 24)))) {
            graphics.centeredText(font, line, centreX, y, Theme.fade(Theme.TEXT_DIM, opacity));
            y += 11;
        }
    }

    private static final int FAIL_ICON_HEIGHT = 27;
    private static final int FAIL_TITLE_HEIGHT = 14;
    private static final int FAIL_LINE_HEIGHT = 11;
    private static final int FAIL_BUTTON_HEIGHT = 18;
    private static final int FAIL_BUTTON_GAP = 9;

    private void renderFailure(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        Browser browser = Browser.get();
        int centreX = page.left() + page.width() / 2;
        int textWidth = Math.max(60, Math.min(360, page.width() - 24));

        String key = browser.failureKey().isBlank() ? "pocketbrowser.error.launch" : browser.failureKey();

        String hintKey = "pocketbrowser.error.signin_busy".equals(key)
                ? "pocketbrowser.error.signin_busy.hint"
                : "pocketbrowser.error.hint";
        List<FormattedCharSequence> hint = new ArrayList<>(
                font.split(Component.translatable(hintKey), textWidth));
        String detail = browser.failureDetail().lines().findFirst().orElse("").trim();
        boolean showIcon = true;

        int available = Math.max(FAIL_BUTTON_HEIGHT, page.height() - 8);
        while (blockHeight(showIcon, hint.size(), !detail.isEmpty()) > available) {
            if (!detail.isEmpty()) {
                detail = "";
            } else if (hint.size() > 1) {
                hint.remove(hint.size() - 1);
            } else if (showIcon) {
                showIcon = false;
            } else if (!hint.isEmpty()) {
                hint.clear();
            } else {
                break;
            }
        }

        int total = blockHeight(showIcon, hint.size(), !detail.isEmpty());
        int y = page.top() + Math.max(4, (page.height() - total) / 2);

        if (showIcon) {
            Icons.draw(graphics, Icons.GLOBE, centreX - 13, y, 3, Theme.fade(Theme.DANGER, opacity * 0.8F));
            y += FAIL_ICON_HEIGHT;
        }
        graphics.centeredText(font, Component.translatable(key), centreX, y, Theme.fade(Theme.TEXT, opacity));
        y += FAIL_TITLE_HEIGHT;

        for (FormattedCharSequence line : hint) {
            graphics.centeredText(font, line, centreX, y, Theme.fade(Theme.TEXT_DIM, opacity));
            y += FAIL_LINE_HEIGHT;
        }
        if (!detail.isEmpty()) {
            graphics.centeredText(font, Theme.ellipsize(font, detail, textWidth), centreX, y + 2,
                    Theme.fade(Theme.TEXT_FAINT, opacity));
            y += FAIL_LINE_HEIGHT + 2;
        }

        y += FAIL_BUTTON_GAP;
        Component label = Component.translatable("pocketbrowser.error.retry");
        int buttonWidth = Math.max(80, font.width(label) + 26);
        retry = new Rect(centreX - buttonWidth / 2, y, centreX + buttonWidth / 2, y + FAIL_BUTTON_HEIGHT);

        boolean hovered = retry.contains(mouseX, mouseY);
        Theme.roundedRect(graphics, retry.x0(), retry.y0(), retry.x1(), retry.y1(),
                Theme.fade(hovered ? Theme.ACCENT_DIM : Theme.CHROME_HIGH, opacity));
        Theme.roundedOutline(graphics, retry.x0(), retry.y0(), retry.x1(), retry.y1(),
                Theme.fade(hovered ? Theme.ACCENT : Theme.BORDER, opacity));
        graphics.centeredText(font, label, retry.centreX(),
                retry.centreY() - font.lineHeight / 2 + 1, Theme.fade(Theme.TEXT, opacity));
    }

    private static int blockHeight(boolean icon, int hintLines, boolean detail) {
        return (icon ? FAIL_ICON_HEIGHT : 0)
                + FAIL_TITLE_HEIGHT
                + hintLines * FAIL_LINE_HEIGHT
                + (detail ? FAIL_LINE_HEIGHT + 2 : 0)
                + FAIL_BUTTON_GAP
                + FAIL_BUTTON_HEIGHT;
    }

    private void renderSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        if (!omnibox.isFocused() || omniboxRect.isEmpty()) {
            suggestions = List.of();
            return;
        }
        suggestions = currentSuggestions();
        if (suggestions.isEmpty()) {
            return;
        }

        int x0 = omniboxRect.x0();
        int x1 = omniboxRect.x1();
        int y0 = suggestionTop;
        int y1 = y0 + suggestions.size() * SUGGESTION_HEIGHT + 4;

        Theme.shadow(graphics, x0, y0, x1, y1, opacity);
        Theme.roundedRect(graphics, x0, y0, x1, y1, Theme.fade(Theme.CHROME_HIGH, opacity));
        Theme.roundedOutline(graphics, x0, y0, x1, y1, Theme.fade(Theme.BORDER, opacity));

        for (int i = 0; i < suggestions.size(); i++) {
            BrowsingData.Entry entry = suggestions.get(i);
            int rowY0 = y0 + 2 + i * SUGGESTION_HEIGHT;
            int rowY1 = rowY0 + SUGGESTION_HEIGHT;
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= rowY0 && mouseY < rowY1;
            if (hovered) {
                highlightedSuggestion = i;
            }
            if (i == highlightedSuggestion || hovered) {
                graphics.fill(x0 + 2, rowY0, x1 - 2, rowY1, Theme.fade(Theme.HOVER, opacity));
            }

            boolean bookmarked = BrowsingData.isBookmarked(entry.url());
            Icons.draw(graphics, bookmarked ? Icons.STAR : Icons.GLOBE, x0 + 5, rowY0 + 3,
                    Theme.fade(bookmarked ? Theme.PINNED : Theme.TEXT_FAINT, opacity));

            String label = entry.title().isBlank() ? Urls.host(entry.url()) : entry.title();
            int textX = x0 + 17;
            int titleWidth = Math.min(font.width(label) + 1, (x1 - textX) / 2);
            int drawn = drawClipped(graphics, Theme.ellipsize(font, label, titleWidth), textX,
                    rowY0 + 4, x1 - 6, Theme.fade(Theme.TEXT, opacity));
            drawClipped(graphics, "  " + Urls.host(entry.url()), drawn, rowY0 + 4, x1 - 6,
                    Theme.fade(Theme.TEXT_FAINT, opacity));
        }
    }

    private List<BrowsingData.Entry> currentSuggestions() {
        String typed = omnibox.text().trim();
        if (typed.isEmpty()) {
            List<BrowsingData.Entry> starting = new ArrayList<>(BrowsingData.bookmarks());
            for (BrowsingData.Entry entry : BrowsingData.history()) {
                if (starting.size() >= MAX_SUGGESTIONS) {
                    break;
                }
                if (starting.stream().noneMatch(had -> had.url().equals(entry.url()))) {
                    starting.add(entry);
                }
            }
            return starting.size() > MAX_SUGGESTIONS ? starting.subList(0, MAX_SUGGESTIONS) : starting;
        }
        return BrowsingData.suggest(typed, MAX_SUGGESTIONS);
    }

    private void renderMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opacity) {
        if (!menuOpen) {
            return;
        }
        List<MenuItem> items = menuItems();
        Rect bounds = menuBounds(items);

        Theme.shadow(graphics, bounds.x0(), bounds.y0(), bounds.x1(), bounds.y1(), opacity);
        Theme.roundedRect(graphics, bounds.x0(), bounds.y0(), bounds.x1(), bounds.y1(),
                Theme.fade(Theme.CHROME_HIGH, opacity));
        Theme.roundedOutline(graphics, bounds.x0(), bounds.y0(), bounds.x1(), bounds.y1(),
                Theme.fade(Theme.BORDER, opacity));

        int zoomY = bounds.y0() + 3;
        Rect minus = zoomMinus(bounds);
        Rect plus = zoomPlus(bounds);
        Rect value = zoomValue(bounds);

        graphics.text(font, Theme.ellipsize(font,
                        Component.translatable("pocketbrowser.menu.zoom").getString(),
                        minus.x0() - bounds.x0() - 12),
                bounds.x0() + 8, zoomY + 6, Theme.fade(Theme.TEXT_DIM, opacity), false);

        iconButton(graphics, minus, Icons.MINUS, Config.get().zoom > 0.26D, mouseX, mouseY, opacity, Theme.TEXT);
        iconButton(graphics, plus, Icons.PLUS, Config.get().zoom < 3.9D, mouseX, mouseY, opacity, Theme.TEXT);
        graphics.centeredText(font, Math.round(Config.get().zoom * 100.0D) + "%",
                value.centreX(), zoomY + 6, Theme.fade(Theme.TEXT, opacity));

        int y = bounds.y0() + 22;
        Theme.separator(graphics, bounds.x0() + 4, bounds.x1() - 4, y - 3, opacity);
        for (MenuItem item : items) {
            int height = menuItemHeight(item);
            int top = y;
            if (item.dividerAbove()) {
                Theme.separator(graphics, bounds.x0() + 4, bounds.x1() - 4, y + 2, opacity);
                top += 5;
            }
            boolean hovered = mouseX >= bounds.x0() && mouseX < bounds.x1()
                    && mouseY >= top && mouseY < top + MENU_ROW;
            if (hovered) {
                graphics.fill(bounds.x0() + 2, top, bounds.x1() - 2, top + MENU_ROW,
                        Theme.fade(Theme.HOVER, opacity));
            }
            graphics.text(font, Theme.ellipsize(font, item.label().getString(), bounds.width() - 16),
                    bounds.x0() + 8, top + (MENU_ROW - font.lineHeight) / 2 + 1,
                    Theme.fade(item.danger() ? Theme.DANGER : Theme.TEXT, opacity), false);
            y += height;
        }
    }

    private void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tooltip(graphics, back, "pocketbrowser.tip.back", mouseX, mouseY);
        tooltip(graphics, forward, "pocketbrowser.tip.forward", mouseX, mouseY);
        tooltip(graphics, reload, "pocketbrowser.tip.reload", mouseX, mouseY);
        tooltip(graphics, home, "pocketbrowser.tip.home", mouseX, mouseY);
        tooltip(graphics, star, "pocketbrowser.tip.bookmark", mouseX, mouseY);
        tooltip(graphics, pin, "pocketbrowser.tip.pin", mouseX, mouseY);
        tooltip(graphics, menu, "pocketbrowser.tip.menu", mouseX, mouseY);
        tooltip(graphics, newTab, "pocketbrowser.tip.new_tab", mouseX, mouseY);
        tooltip(graphics, maximizeWindow, Config.get().winMaximized
                ? "pocketbrowser.tip.restore" : "pocketbrowser.tip.maximize", mouseX, mouseY);
        tooltip(graphics, closeWindow, "pocketbrowser.tip.close", mouseX, mouseY);
    }

    private void tooltip(GuiGraphicsExtractor graphics, Rect rect, String key, int mouseX, int mouseY) {
        if (!rect.isEmpty() && rect.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable(key), mouseX, mouseY);
        }
    }

    private void iconButton(GuiGraphicsExtractor graphics, Rect rect, String[] icon, boolean enabled,
                            int mouseX, int mouseY, float opacity, int color) {
        if (rect.isEmpty()) {
            return;
        }
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        Theme.controlSurface(graphics, rect.x0(), rect.y0(), rect.x1(), rect.y1(), hovered, opacity);
        int tint = enabled ? color : Theme.mix(color, Theme.WINDOW, 0.62F);
        Icons.centered(graphics, icon, rect.x0(), rect.y0(), rect.x1(), rect.y1(), Theme.fade(tint, opacity));
    }

    private record MenuItem(Component label, Runnable action, boolean danger, boolean dividerAbove) {
        MenuItem(Component label, Runnable action) {
            this(label, action, false, false);
        }
    }

    private List<MenuItem> menuItems() {
        Config config = Config.get();
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.new_tab"),
                () -> Browser.get().newTab(config.homePage)));
        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.sign_in"), this::signIn));

        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.set_home"),
                this::setHomeToCurrent, false, true));
        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.system_browser"), this::openInSystem));
        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.clear_history"),
                BrowsingData::clearHistory, true, false));

        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.settings"),
                this::openSettings, false, true));
        items.add(new MenuItem(Component.translatable("pocketbrowser.menu.restart"),
                () -> Browser.get().restart(), true, false));
        return items;
    }

    private static int menuItemHeight(MenuItem item) {
        return MENU_ROW + (item.dividerAbove() ? 5 : 0);
    }

    private Rect menuBounds(List<MenuItem> items) {
        int x1 = menu.x1();
        int x0 = Math.max(winX0 + 4, x1 - MENU_WIDTH);
        int contentHeight = 25;
        for (MenuItem item : items) {
            contentHeight += menuItemHeight(item);
        }

        int y0 = Math.min(menu.y1() + 2, winY1 - 2 - contentHeight);
        y0 = Math.max(y0, winY0 + 2);
        return new Rect(x0, y0, x1, y0 + contentHeight);
    }

    private Rect zoomPlus(Rect bounds) {
        int y = bounds.y0() + 2;
        int right = bounds.x1() - 6;
        return new Rect(right - MENU_STEP, y, right, y + 18);
    }

    private Rect zoomValue(Rect bounds) {
        int y = bounds.y0() + 2;
        int right = zoomPlus(bounds).x0() - 4;
        return new Rect(right - MENU_VALUE, y, right, y + 18);
    }

    private Rect zoomMinus(Rect bounds) {
        int y = bounds.y0() + 2;
        int right = zoomValue(bounds).x0() - 4;
        return new Rect(right - MENU_STEP, y, right, y + 18);
    }

    private void openSettings() {
        if (minecraft != null) {
            GameCompatibility.openScreen(minecraft, new SettingsScreen(this));
        }
    }

    private void setHomeToCurrent() {
        BrowserTab tab = Browser.get().activeTab();
        if (tab != null && !Urls.isBlank(tab.url())) {
            Config config = Config.get();
            config.homePage = tab.url();
            config.save();
        }
    }

    private void signIn() {
        BrowserTab tab = Browser.get().activeTab();
        Browser.get().startSignIn(tab == null ? null : tab.url());
    }

    private void openInSystem() {
        BrowserTab tab = Browser.get().activeTab();
        if (tab == null || Urls.isBlank(tab.url())) {
            return;
        }
        try {
            Util.getPlatform().openUri(URI.create(tab.url()));
        } catch (RuntimeException e) {
            PocketBrowser.LOG.warn("Could not hand {} to the system browser", tab.url(), e);
        }
    }

    private void changeZoom(double delta) {
        Config config = Config.get();
        config.zoom = Math.clamp(Math.round((config.zoom + delta) * 100.0D) / 100.0D, 0.25D, 4.0D);
        config.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (animating()) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int modifiers = CdpKeys.modifiers(event.modifiers());

        if (menuOpen && handleMenuClick(mouseX, mouseY)) {
            return true;
        }
        if (menuOpen) {
            menuOpen = false;
            return true;
        }

        if (omnibox.isFocused() && handleSuggestionClick(mouseX, mouseY)) {
            return true;
        }

        if (handleChromeClick(mouseX, mouseY, button)) {
            return true;
        }

        if (findVisible && handleFindClick(mouseX, mouseY, button)) {
            return true;
        }

        if (!Config.get().winMaximized && resizeGrip().contains(mouseX, mouseY)) {
            drag = Drag.RESIZE;
            grabX = mouseX - winX1;
            grabY = mouseY - winY1;
            return true;
        }
        if (mouseY >= winY0 && mouseY < tabsY1 && mouseX >= winX0 && mouseX < winX1) {
            if (doubleClick) {
                click();
                toggleMaximized();
                return true;
            }
            beginMove(mouseX, mouseY);
            return true;
        }

        if (page.contains(mouseX, mouseY)) {
            Browser browser = Browser.get();
            if (browser.status() == Browser.Status.FAILED && retry.contains(mouseX, mouseY)) {
                click();
                browser.restart();
                return true;
            }
            omnibox.setFocused(false);
            finder.setFocused(false);

            boolean ctrlClick = button == 0 && (modifiers & 2) != 0;
            if (button == 2 || ctrlClick) {
                page.openLinkUnder(browser.activeTab(), mouseX, mouseY, ctrlClick, modifiers);
                return true;
            }

            page.mousePressed(browser.activeTab(), mouseX, mouseY, button, doubleClick ? 2 : 1, modifiers);
            return true;
        }

        if (mouseX < winX0 || mouseX >= winX1 || mouseY < winY0 || mouseY >= winY1) {
            beginClose();
            return true;
        }
        return true;
    }

    private boolean handleChromeClick(double mouseX, double mouseY, int button) {
        Browser browser = Browser.get();
        BrowserTab tab = browser.activeTab();

        if (closeWindow.contains(mouseX, mouseY)) {
            click();
            beginClose();
            return true;
        }
        if (maximizeWindow.contains(mouseX, mouseY)) {
            click();
            toggleMaximized();
            return true;
        }
        if (newTab.contains(mouseX, mouseY)) {
            if (browser.tabs().size() < MAX_TABS) {
                click();
                browser.newTab(Config.get().homePage);
                layout();
            }
            return true;
        }
        if (back.contains(mouseX, mouseY)) {
            if (tab != null && tab.canGoBack()) {
                click();
                tab.back();
            }
            return true;
        }
        if (forward.contains(mouseX, mouseY)) {
            if (tab != null && tab.canGoForward()) {
                click();
                tab.forward();
            }
            return true;
        }
        if (reload.contains(mouseX, mouseY)) {
            if (tab != null) {
                click();
                if (tab.loading()) {
                    tab.stopLoading();
                } else {
                    tab.reload(false);
                }
            }
            return true;
        }
        if (!home.isEmpty() && home.contains(mouseX, mouseY)) {
            if (tab != null) {
                click();
                tab.navigate(Config.get().homePage);
            }
            return true;
        }
        if (!star.isEmpty() && star.contains(mouseX, mouseY)) {
            if (tab != null && !Urls.isBlank(tab.url())) {
                click();
                BrowsingData.toggleBookmark(tab.url(), tab.title());
            }
            return true;
        }
        if (pin.contains(mouseX, mouseY)) {
            click();
            togglePin();
            return true;
        }
        if (menu.contains(mouseX, mouseY)) {
            click();
            menuOpen = true;
            return true;
        }
        if (!omniboxRect.isEmpty() && omniboxRect.contains(mouseX, mouseY)) {
            finder.setFocused(false);
            if (!omnibox.isFocused()) {
                omnibox.setText(tab == null || Urls.isBlank(tab.url()) ? "" : tab.url());
                omnibox.focusAndSelectAll();
            } else {
                omnibox.placeCursorAt(font, mouseX, false);
            }
            highlightedSuggestion = -1;
            return true;
        }

        List<BrowserTab> tabs = browser.tabs();
        if (mouseY >= tabsY0 && mouseY < tabsY1) {
            for (int i = 0; i < tabs.size(); i++) {
                int x0 = tabsAreaX0 + i * tabWidth;
                int x1 = x0 + tabWidth - 2;
                if (x1 > tabLimit || mouseX < x0 || mouseX >= x1) {
                    continue;
                }
                boolean closable = tabWidth >= 52;
                if (button == 2 || (closable && mouseX >= x1 - 15)) {
                    click();
                    browser.closeTab(tabs.get(i));
                } else {
                    click();
                    browser.select(i);
                }
                layout();
                return true;
            }
        }
        return false;
    }

    private boolean handleSuggestionClick(double mouseX, double mouseY) {
        if (suggestions.isEmpty() || omniboxRect.isEmpty()) {
            return false;
        }
        int x0 = omniboxRect.x0();
        int x1 = omniboxRect.x1();
        int y0 = suggestionTop;
        for (int i = 0; i < suggestions.size(); i++) {
            int rowY0 = y0 + 2 + i * SUGGESTION_HEIGHT;
            if (mouseX >= x0 && mouseX < x1 && mouseY >= rowY0 && mouseY < rowY0 + SUGGESTION_HEIGHT) {
                click();
                navigateTo(suggestions.get(i).url());
                return true;
            }
        }
        return false;
    }

    private boolean handleFindClick(double mouseX, double mouseY, int button) {
        if (findClose.contains(mouseX, mouseY)) {
            click();
            setFindVisible(false);
            return true;
        }
        if (findPrev.contains(mouseX, mouseY)) {
            runFind(false);
            return true;
        }
        if (findNext.contains(mouseX, mouseY)) {
            runFind(true);
            return true;
        }
        if (findFieldRect.contains(mouseX, mouseY)) {
            omnibox.setFocused(false);
            if (!finder.isFocused()) {
                finder.focusAndSelectAll();
            } else {
                finder.placeCursorAt(font, mouseX, false);
            }
            return true;
        }
        return false;
    }

    private boolean handleMenuClick(double mouseX, double mouseY) {
        List<MenuItem> items = menuItems();
        Rect bounds = menuBounds(items);
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (zoomMinus(bounds).contains(mouseX, mouseY)) {
            click();
            changeZoom(-0.1D);
            return true;
        }
        if (zoomPlus(bounds).contains(mouseX, mouseY)) {
            click();
            changeZoom(0.1D);
            return true;
        }
        int y = bounds.y0() + 22;
        for (MenuItem item : items) {
            int top = y + (item.dividerAbove() ? 5 : 0);
            if (mouseY >= top && mouseY < top + MENU_ROW) {
                click();
                menuOpen = false;
                item.action().run();
                layout();
                return true;
            }
            y += menuItemHeight(item);
        }
        return true;
    }

    private Rect resizeGrip() {
        return new Rect(winX1 - GRIP, winY1 - GRIP, winX1, winY1);
    }

    private void beginMove(double mouseX, double mouseY) {
        Config config = Config.get();
        if (config.winMaximized) {
            config.winMaximized = false;
            config.winWidth = winX1 - winX0;
            config.winHeight = winY1 - winY0;
            config.winX = winX0;
            config.winY = winY0;
        }
        drag = Drag.MOVE;
        grabX = mouseX - winX0;
        grabY = mouseY - winY0;
    }

    private void toggleMaximized() {
        Config config = Config.get();
        if (!config.winMaximized) {
            config.winWidth = winX1 - winX0;
            config.winHeight = winY1 - winY0;
            config.winX = winX0;
            config.winY = winY0;
        }
        config.winMaximized = !config.winMaximized;
        config.save();
        layout();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            Config.get().save();
            return true;
        }
        if (!animating() && page.isDragging()) {
            page.mouseReleased(Browser.get().activeTab(), event.x(), event.y(),
                    event.button(), CdpKeys.modifiers(event.modifiers()));
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (animating()) {
            return true;
        }
        if (drag != Drag.NONE) {
            Config config = Config.get();
            if (drag == Drag.MOVE) {
                config.winX = (int) Math.round(event.x() - grabX);
                config.winY = (int) Math.round(event.y() - grabY);
            } else {
                config.winWidth = Math.max(MIN_WINDOW_WIDTH,
                        (int) Math.round(event.x() - grabX) - winX0);
                config.winHeight = Math.max(MIN_WINDOW_HEIGHT,
                        (int) Math.round(event.y() - grabY) - winY0);
            }
            layout();
            return true;
        }
        if (page.isDragging()) {
            page.mouseMoved(Browser.get().activeTab(), event.x(), event.y(),
                    CdpKeys.modifiers(event.modifiers()));
            return true;
        }
        if (omnibox.isFocused() && !omniboxRect.isEmpty() && event.y() >= omniboxRect.y0()
                && event.y() < omniboxRect.y1()) {
            omnibox.placeCursorAt(font, event.x(), true);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!animating() && !page.isDragging() && page.contains(mouseX, mouseY)) {
            page.mouseMoved(Browser.get().activeTab(), mouseX, mouseY, 0);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (animating()) {
            return true;
        }
        if (page.contains(mouseX, mouseY)) {
            if (controlDown()) {
                changeZoom(scrollY > 0.0D ? 0.1D : -0.1D);
                return true;
            }
            page.scroll(Browser.get().activeTab(), mouseX, mouseY, scrollX, scrollY, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (animating()) {
            return true;
        }
        Browser browser = Browser.get();
        BrowserTab tab = browser.activeTab();

        if (finder.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                runFind(!event.hasShiftDown());
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                setFindVisible(false);
                return true;
            }
            if (finder.keyPressed(event)) {
                return true;
            }
        }

        if (omnibox.isFocused()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    commitOmnibox();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    omnibox.setFocused(false);
                    highlightedSuggestion = -1;
                    if (tab != null) {
                        omnibox.setText(Urls.isBlank(tab.url()) ? "" : tab.url());
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    moveSuggestion(1);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    moveSuggestion(-1);
                    return true;
                }
                default -> {
                    if (omnibox.keyPressed(event)) {
                        highlightedSuggestion = -1;
                        return true;
                    }
                }
            }
        }

        if (handleShortcut(event, tab)) {
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            beginClose();
            return true;
        }

        if (tab != null && !omnibox.isFocused() && !finder.isFocused()) {
            CdpKeys.Key key = CdpKeys.of(event.key());
            if (key != null) {
                tab.keyDown(key, CdpKeys.modifiers(event.modifiers()));
                return true;
            }
        }
        return false;
    }

    private boolean handleShortcut(KeyEvent event, @Nullable BrowserTab tab) {
        Browser browser = Browser.get();
        boolean control = event.hasControlDown();

        if (control && tab != null) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_T -> {
                    if (browser.tabs().size() < MAX_TABS) {
                        browser.newTab(Config.get().homePage);
                        layout();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_W -> {
                    browser.closeTab(tab);
                    layout();
                    return true;
                }
                case GLFW.GLFW_KEY_L -> {
                    finder.setFocused(false);
                    omnibox.setText(Urls.isBlank(tab.url()) ? "" : tab.url());
                    omnibox.focusAndSelectAll();
                    return true;
                }
                case GLFW.GLFW_KEY_R -> {
                    tab.reload(event.hasShiftDown());
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    setFindVisible(true);
                    return true;
                }
                case GLFW.GLFW_KEY_D -> {
                    BrowsingData.toggleBookmark(tab.url(), tab.title());
                    return true;
                }
                case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                    changeZoom(0.1D);
                    return true;
                }
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                    changeZoom(-0.1D);
                    return true;
                }
                case GLFW.GLFW_KEY_0 -> {
                    Config config = Config.get();
                    config.zoom = 1.0D;
                    config.save();
                    return true;
                }
                default -> {
                }
            }
        }

        if (tab != null && !omnibox.isFocused() && !finder.isFocused()) {
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
        }

        if (tab != null && event.hasAltDown()) {
            if (event.key() == GLFW.GLFW_KEY_LEFT) {
                tab.back();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_RIGHT) {
                tab.forward();
                return true;
            }
        }
        if (tab != null && event.key() == GLFW.GLFW_KEY_F5) {
            tab.reload(event.hasControlDown());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!animating() && !omnibox.isFocused() && !finder.isFocused()) {
            BrowserTab tab = Browser.get().activeTab();
            CdpKeys.Key key = CdpKeys.of(event.key());
            if (tab != null && key != null) {
                tab.keyUp(key, CdpKeys.modifiers(event.modifiers()));
                return true;
            }
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (animating()) {
            return true;
        }
        if (finder.charTyped(event.codepoint())) {
            return true;
        }
        if (omnibox.charTyped(event.codepoint())) {
            highlightedSuggestion = -1;
            return true;
        }
        BrowserTab tab = Browser.get().activeTab();
        if (tab != null) {
            tab.typed(event.codepointAsString());
            return true;
        }
        return super.charTyped(event);
    }

    private void commitOmnibox() {
        if (highlightedSuggestion >= 0 && highlightedSuggestion < suggestions.size()) {
            navigateTo(suggestions.get(highlightedSuggestion).url());
            return;
        }
        navigateTo(Urls.resolve(omnibox.text(), Config.get().searchTemplate));
    }

    private void navigateTo(String url) {
        BrowserTab tab = Browser.get().activeTab();
        if (tab != null) {
            tab.navigate(url);
        }
        omnibox.setFocused(false);
        omnibox.setText(url);
        highlightedSuggestion = -1;
    }

    private void moveSuggestion(int delta) {
        if (suggestions.isEmpty()) {
            return;
        }
        int next = highlightedSuggestion + delta;
        highlightedSuggestion = next < -1 ? suggestions.size() - 1 : (next >= suggestions.size() ? -1 : next);
    }

    private void setFindVisible(boolean visible) {
        if (findVisible == visible) {
            if (visible) {
                finder.focusAndSelectAll();
            }
            return;
        }
        findVisible = visible;
        if (visible) {
            omnibox.setFocused(false);
            finder.focusAndSelectAll();
        } else {
            finder.setFocused(false);
        }
        layout();
    }

    private void runFind(boolean forward) {
        BrowserTab tab = Browser.get().activeTab();
        if (tab != null && !finder.text().isBlank()) {
            tab.find(finder.text(), forward);
        }
    }

    private void togglePin() {
        Config config = Config.get();
        config.pinEnabled = !config.pinEnabled;
        config.save();
        if (config.pinEnabled && minecraft != null && minecraft.level != null) {
            beginClose();
        }
    }
}
