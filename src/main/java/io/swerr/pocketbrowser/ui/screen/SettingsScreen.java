package io.swerr.pocketbrowser.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.config.QualityPreset;
import io.swerr.pocketbrowser.engine.ChromeFinder;
import io.swerr.pocketbrowser.engine.Browser;
import io.swerr.pocketbrowser.ui.theme.Theme;
import io.swerr.pocketbrowser.ui.theme.ThemeStyle;
import io.swerr.pocketbrowser.ui.widget.Label;
import io.swerr.pocketbrowser.ui.widget.OptionButton;
import io.swerr.pocketbrowser.ui.widget.TabHeader;
import io.swerr.pocketbrowser.ui.widget.ThemedButton;
import io.swerr.pocketbrowser.util.GameCompatibility;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 320;
    private static final int CONTROL_WIDTH = 118;
    private static final int GUTTER = 8;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_TOP = 6;
    private static final int TITLE_TOP = 33;
    private static final int CONTENT_TOP = 48;
    private static final int FOOTER = 36;

    private static final int[] QUALITY = {40, 50, 60, 72, 80, 88, 92, 96, 100};
    private static final int[] FPS = {10, 15, 20, 24, 30, 45, 60};
    private static final int[] FRAME_WIDTH = {960, 1280, 1600, 1920, 2560, 3840, 0};
    private static final int[] ZOOM = {50, 67, 75, 90, 100, 110, 125, 150, 175, 200};
    private static final int[] OPACITY = {20, 30, 40, 50, 60, 70, 80, 90, 100};

    private static final String[] TAB_KEYS = {
            "pocketbrowser.settings.tab.picture",
            "pocketbrowser.settings.tab.browser",
            "pocketbrowser.settings.tab.interface",
            "pocketbrowser.settings.tab.pin",
    };

    private record Row(Label label, AbstractWidget control) {
    }

    @Nullable
    private final Screen parent;
    private final List<TabHeader> tabs = new ArrayList<>();
    private final List<List<Row>> pages = new ArrayList<>();
    private int selectedTab;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("pocketbrowser.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        tabs.clear();
        pages.clear();

        Config config = Config.get();
        pages.add(pictureOptions(config));
        pages.add(browserOptions(config));
        pages.add(interfaceOptions(config));
        pages.add(pinOptions(config));

        int tabWidth = Math.min(110, Math.min(width - 12, 460) / TAB_KEYS.length);
        int left = width / 2 - tabWidth * TAB_KEYS.length / 2;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int index = i;
            TabHeader tab = new TabHeader(left + i * tabWidth, TAB_TOP, tabWidth - 1, TAB_HEIGHT,
                    Component.translatable(TAB_KEYS[i]), () -> selectTab(index));
            tabs.add(tab);
            addRenderableWidget(tab);
        }

        for (List<Row> page : pages) {
            for (Row row : page) {
                addRenderableWidget(row.label());
                addRenderableWidget(row.control());
            }
        }

        addRenderableWidget(new ThemedButton(width / 2 - 100, height - 28, 200, 20,
                CommonComponents.GUI_DONE, this::onClose));

        selectTab(selectedTab);
    }

    private void selectTab(int index) {
        selectedTab = Math.clamp(index, 0, pages.size() - 1);
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setSelected(i == selectedTab);
        }

        for (int i = 0; i < pages.size(); i++) {
            List<Row> page = pages.get(i);
            if (i != selectedTab) {
                for (Row row : page) {
                    hide(row);
                }
                continue;
            }
            layout(page);
        }
    }

    private static void hide(Row row) {
        row.label().visible = false;
        row.control().visible = false;
        row.control().active = false;
    }

    private void layout(List<Row> page) {
        int room = Math.max(40, height - FOOTER - CONTENT_TOP);
        int rowHeight = Math.clamp(room / Math.max(1, page.size()), 14, ROW_HEIGHT);
        int controlHeight = Math.max(12, rowHeight - 4);

        int rowWidth = Math.min(width - 24, ROW_WIDTH);
        int controlWidth = Math.min(CONTROL_WIDTH, Math.max(48, rowWidth / 2));
        int labelWidth = Math.max(20, rowWidth - controlWidth - GUTTER);
        int left = (width - rowWidth) / 2;

        for (int slot = 0; slot < page.size(); slot++) {
            Row row = page.get(slot);
            int top = CONTENT_TOP + slot * rowHeight;

            row.label().visible = true;
            row.label().setPosition(left, top);
            row.label().setSize(labelWidth, controlHeight);

            row.control().visible = true;
            row.control().active = true;
            row.control().setPosition(left + rowWidth - controlWidth, top);
            row.control().setSize(controlWidth, controlHeight);
        }
    }

    private List<Row> pictureOptions(Config config) {
        List<Row> page = new ArrayList<>();

        page.add(row("pocketbrowser.settings.preset", null,
                new OptionButton<>(List.of(QualityPreset.values()), currentPreset(),
                        QualityPreset::label, this::applyPreset)));

        page.add(numbers("pocketbrowser.settings.quality", QUALITY, config.frameQuality,
                value -> Component.literal(value + "%"),
                value -> {
                    config.frameQuality = value;
                    commitImage(config);
                }));

        page.add(row("pocketbrowser.settings.format", "pocketbrowser.settings.format.tip",
                new OptionButton<>(List.of(Boolean.FALSE, Boolean.TRUE), "png".equals(config.frameFormat),
                        png -> Component.literal(png ? "PNG" : "JPEG"),
                        png -> {
                            config.frameFormat = png ? "png" : "jpeg";
                            commitImage(config);
                        })));

        page.add(numbers("pocketbrowser.settings.fps", FPS, config.maxFps,
                value -> Component.literal(String.valueOf(value)),
                value -> {
                    config.maxFps = value;
                    commitImage(config);
                }));

        page.add(numbers("pocketbrowser.settings.max_width", FRAME_WIDTH, config.maxFrameWidth,
                value -> value == 0
                        ? Component.translatable("pocketbrowser.settings.unlimited")
                        : Component.literal(String.valueOf(value)),
                value -> {
                    config.maxFrameWidth = value;
                    commitImage(config);
                }));

        page.add(numbers("pocketbrowser.settings.zoom", ZOOM,
                (int) Math.round(config.zoom * 100.0D),
                value -> Component.literal(value + "%"),
                value -> {
                    config.zoom = value / 100.0D;
                    config.save();
                }));

        page.add(onOff("pocketbrowser.settings.still", "pocketbrowser.settings.still.tip",
                config.sharpenWhenStill, value -> {
                    config.sharpenWhenStill = value;
                    config.save();
                }));

        page.add(onOff("pocketbrowser.settings.video_quality", "pocketbrowser.settings.video_quality.tip",
                config.bestVideoQuality, value -> {
                    config.bestVideoQuality = value;
                    config.save();
                }));
        return page;
    }

    private List<Row> browserOptions(Config config) {
        List<Row> page = new ArrayList<>();

        List<String> browsers = new ArrayList<>();
        browsers.add("");
        for (ChromeFinder.Found found : ChromeFinder.findAll()) {
            browsers.add(found.path().toString());
        }
        page.add(row("pocketbrowser.settings.engine", "pocketbrowser.settings.engine.tip",
                new OptionButton<>(browsers, browsers.contains(config.browserPath) ? config.browserPath : "",
                        SettingsScreen::browserLabel,
                        path -> {
                            config.browserPath = path;
                            config.save();
                            restartIfRunning();
                        })));

        page.add(onOff("pocketbrowser.settings.real_window", "pocketbrowser.settings.real_window.tip",
                !config.headless, value -> {
                    config.headless = !value;
                    config.save();
                    restartIfRunning();
                }));

        page.add(onOff("pocketbrowser.settings.restore", "pocketbrowser.settings.restore.tip",
                config.restoreLastPage, value -> {
                    config.restoreLastPage = value;
                    config.save();
                }));
        return page;
    }

    private List<Row> interfaceOptions(Config config) {
        List<Row> page = new ArrayList<>();

        page.add(row("pocketbrowser.settings.theme", "pocketbrowser.settings.theme.tip",
                new OptionButton<>(List.of(ThemeStyle.values()), ThemeStyle.from(config.uiTheme), ThemeStyle::label,
                        theme -> {
                            config.uiTheme = theme.id;
                            config.save();
                            Theme.apply(theme);
                        })));

        page.add(onOff("pocketbrowser.settings.pause_button", null,
                config.pauseMenuButton, value -> {
                    config.pauseMenuButton = value;
                    config.save();
                }));

        page.add(onOff("pocketbrowser.settings.title_button", null,
                config.titleScreenButton, value -> {
                    config.titleScreenButton = value;
                    config.save();
                }));
        return page;
    }

    private List<Row> pinOptions(Config config) {
        List<Row> page = new ArrayList<>();

        page.add(onOff("pocketbrowser.settings.pin_hide", null,
                config.pinHideWithHud, value -> {
                    config.pinHideWithHud = value;
                    config.save();
                }));

        page.add(onOff("pocketbrowser.settings.pin_dim", "pocketbrowser.settings.pin_dim.tip",
                config.pinDimWhenUnfocused, value -> {
                    config.pinDimWhenUnfocused = value;
                    config.save();
                }));

        page.add(numbers("pocketbrowser.settings.pin_opacity", OPACITY,
                (int) Math.round(config.pinOpacity * 100.0D),
                value -> Component.literal(value + "%"),
                value -> {
                    config.pinOpacity = value / 100.0D;
                    config.save();
                }));
        return page;
    }

    private Row row(String key, @Nullable String tooltipKey, AbstractWidget control) {
        if (tooltipKey != null) {
            control.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
        return new Row(new Label(Component.translatable(key)), control);
    }

    private Row onOff(String key, @Nullable String tooltipKey, boolean initial, Consumer<Boolean> apply) {
        return row(key, tooltipKey, new OptionButton<>(List.of(Boolean.TRUE, Boolean.FALSE), initial,
                value -> value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF, apply));
    }

    private Row numbers(String key, int[] values, int current,
                        Function<Integer, Component> naming, IntConsumer apply) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return row(key, key + ".tip", new OptionButton<>(list, nearest(values, current), naming,
                value -> apply.accept(value)));
    }

    private static int nearest(int[] values, int current) {
        int best = values[0];
        for (int value : values) {
            if (Math.abs(value - current) < Math.abs(best - current)) {
                best = value;
            }
        }
        return best;
    }

    private static Component browserLabel(String path) {
        if (path == null || path.isBlank()) {
            return Component.translatable("pocketbrowser.settings.engine.auto");
        }
        for (ChromeFinder.Found found : ChromeFinder.findAll()) {
            if (found.path().toString().equals(path)) {
                return Component.literal(found.name());
            }
        }
        return Component.translatable("pocketbrowser.settings.engine.custom");
    }

    private static void restartIfRunning() {
        if (Browser.get().status() != Browser.Status.IDLE) {
            Browser.get().restart();
        }
    }

    private static QualityPreset currentPreset() {
        QualityPreset preset = QualityPreset.current(Config.get());
        return preset == null ? QualityPreset.BALANCED : preset;
    }

    private void applyPreset(QualityPreset preset) {
        Config config = Config.get();
        preset.applyTo(config);
        commitImage(config);
        rebuildWidgets();
    }

    private void commitImage(Config config) {
        config.save();
        Browser.get().restartStream();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (!Theme.isVanilla()) {
            graphics.fill(0, 0, width, height, Theme.SCRIM);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, TITLE_TOP,
                Theme.isVanilla() ? 0xFFFFFFFF : Theme.TEXT);
        Theme.separator(graphics, 0, width, height - FOOTER, 1.0F);
    }

    @Override
    protected void repositionElements() {
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        Config.get().save();
        if (minecraft != null) {
            GameCompatibility.openScreen(minecraft, parent);
        }
    }
}
