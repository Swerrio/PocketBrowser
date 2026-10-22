package io.swerr.pocketbrowser.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.data.BrowsingData;
import io.swerr.pocketbrowser.engine.BrowserTab;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.ui.PinnedHud;
import io.swerr.pocketbrowser.ui.PinnedWindow;
import io.swerr.pocketbrowser.ui.screen.BrowserScreen;
import io.swerr.pocketbrowser.ui.screen.PinnedScreen;
import io.swerr.pocketbrowser.ui.theme.Theme;
import io.swerr.pocketbrowser.ui.theme.ThemeStyle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import io.swerr.pocketbrowser.util.GameCompatibility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLScancode;

public class PocketBrowserClient implements ClientModInitializer {
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_MARGIN = 4;

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(PocketBrowser.id("main"));

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.pocketbrowser.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_B, CATEGORY);

    public static final KeyMapping PIN_TOGGLE_KEY = new KeyMapping(
            "key.pocketbrowser.pin", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_N, CATEGORY);

    public static final KeyMapping PIN_FOCUS_KEY = new KeyMapping(
            "key.pocketbrowser.pin_focus", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_M, CATEGORY);

    public static final KeyMapping PLAY_PAUSE_KEY = new KeyMapping(
            "key.pocketbrowser.play_pause", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_P, CATEGORY);

    public static final KeyMapping SEEK_BACK_KEY = new KeyMapping(
            "key.pocketbrowser.seek_back", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_LEFTBRACKET, CATEGORY);

    public static final KeyMapping SEEK_FORWARD_KEY = new KeyMapping(
            "key.pocketbrowser.seek_forward", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_RIGHTBRACKET, CATEGORY);

    public static final KeyMapping MUTE_KEY = new KeyMapping(
            "key.pocketbrowser.mute", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_O, CATEGORY);

    @Override
    public void onInitializeClient() {
        Theme.apply(ThemeStyle.from(Config.get().uiTheme));

        KeyMappingHelper.registerKeyMapping(OPEN_KEY);
        KeyMappingHelper.registerKeyMapping(PIN_TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(PIN_FOCUS_KEY);
        KeyMappingHelper.registerKeyMapping(PLAY_PAUSE_KEY);
        KeyMappingHelper.registerKeyMapping(SEEK_BACK_KEY);
        KeyMappingHelper.registerKeyMapping(SEEK_FORWARD_KEY);
        KeyMappingHelper.registerKeyMapping(MUTE_KEY);

        HudElementRegistry.addLast(PocketBrowser.id("pinned_browser"), new PinnedHud());

        ClientTickEvents.END_CLIENT_TICK.register(PocketBrowserClient::onClientTick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> onScreenInit(screen));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Browser.get().rememberLastPage();
            BrowsingData.flush();
            Browser.get().shutdownAndWait();
        });
    }

    public static String pinFocusKeyName() {
        return PIN_FOCUS_KEY.getTranslatedKeyMessage().getString();
    }

    private static void onClientTick(Minecraft minecraft) {
        Browser browser = Browser.get();
        Screen screen = GameCompatibility.screen(minecraft);
        boolean ours = screen instanceof BrowserScreen || screen instanceof PinnedScreen;
        Config config = Config.get();

        browser.tick(ours || config.pinEnabled);

        if (config.pinEnabled && browser.status() == Browser.Status.IDLE && minecraft.level != null) {
            browser.start();
        }

        while (OPEN_KEY.consumeClick()) {
            if (screen == null) {
                GameCompatibility.openScreen(minecraft, new BrowserScreen(null));
            }
        }
        while (PIN_TOGGLE_KEY.consumeClick()) {
            if (screen == null) {
                config.pinEnabled = !config.pinEnabled;
                config.save();
                if (config.pinEnabled) {
                    browser.start();
                }
            }
        }
        while (PIN_FOCUS_KEY.consumeClick()) {
            if (screen == null && config.pinEnabled) {
                GameCompatibility.openScreen(minecraft, new PinnedScreen());
            }
        }

        handleMediaKeys(browser);
    }

    private static void handleMediaKeys(Browser browser) {
        BrowserTab tab = browser.status() == Browser.Status.READY ? browser.activeTab() : null;

        boolean play = false;
        boolean mute = false;
        double seek = 0.0D;
        while (PLAY_PAUSE_KEY.consumeClick()) {
            play = true;
        }
        while (MUTE_KEY.consumeClick()) {
            mute = true;
        }
        while (SEEK_BACK_KEY.consumeClick()) {
            seek -= 10.0D;
        }
        while (SEEK_FORWARD_KEY.consumeClick()) {
            seek += 10.0D;
        }

        if (tab == null) {
            return;
        }
        if (play) {
            tab.mediaTogglePlay();
        }
        if (mute) {
            tab.mediaToggleMute();
        }
        if (seek != 0.0D) {
            tab.mediaSeek(seek);
        }
        if (play || mute || seek != 0.0D) {
            PinnedWindow.nudgeMedia();
        }
    }

    private static void onScreenInit(Screen screen) {
        Config config = Config.get();
        if (screen instanceof PauseScreen && config.pauseMenuButton) {
            addButton(screen, screen.width - BUTTON_SIZE - BUTTON_MARGIN,
                    screen.height - BUTTON_SIZE - BUTTON_MARGIN);
        } else if (screen instanceof TitleScreen && config.titleScreenButton) {
            addButton(screen, screen.width - BUTTON_SIZE - BUTTON_MARGIN, BUTTON_MARGIN);
        }
    }

    private static void addButton(Screen screen, int x, int y) {
        SpriteIconButton button = SpriteIconButton
                .builder(Component.translatable("pocketbrowser.title"),
                        press -> open(screen, x + BUTTON_SIZE / 2.0D, y + BUTTON_SIZE / 2.0D), true)
                .width(BUTTON_SIZE)
                .sprite(PocketBrowser.id("icon/globe"), 15, 15)
                .narration(narration -> Component.translatable("pocketbrowser.title"))
                .build();
        button.setTooltip(Tooltip.create(Component.translatable("pocketbrowser.button.tooltip")));
        button.setPosition(x, y);
        Screens.getWidgets(screen).add(button);
    }

    private static void open(Screen parent, double summonX, double summonY) {
        GameCompatibility.openScreen(Minecraft.getInstance(), new BrowserScreen(parent, summonX, summonY));
    }
}
