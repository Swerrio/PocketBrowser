package io.swerr.pocketbrowser.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.util.AtomicFile;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public String homePage = "https://www.google.com/";

    public String searchTemplate = "https://www.google.com/search?q=%s";

    public String browserPath = "";

    public boolean headless = true;

    public volatile String frameFormat = "jpeg";

    public int frameQuality = 88;

    public int maxFps = 30;

    public int maxFrameWidth = 0;

    public boolean sharpenWhenStill = true;

    public boolean bestVideoQuality = true;

    public int startupTimeoutSeconds = 25;

    public int idleShutdownMinutes = 10;

    public String uiTheme = "minecraft";

    public boolean pauseMenuButton = true;

    public boolean titleScreenButton = true;

    public double zoom = 1.0D;

    public boolean winMaximized = true;
    public int winX = 0;
    public int winY = 0;
    public int winWidth = 0;
    public int winHeight = 0;

    public boolean restoreLastPage = true;

    public String lastPage = "";

    public boolean pinEnabled = false;

    public int pinX = 8;
    public int pinY = 8;
    public int pinWidth = 220;
    public int pinHeight = 140;

    public double pinOpacity = 1.0D;

    public boolean pinHideWithHud = true;

    public boolean pinDimWhenUnfocused = false;

    public static Config get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Config load() {
        Path path = PocketBrowser.configFile();
        if (Files.isRegularFile(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                Config loaded = GSON.fromJson(json, Config.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (Exception e) {
                PocketBrowser.LOG.warn("Could not read {}, falling back to defaults", path, e);
            }
        }
        Config fresh = new Config();
        fresh.save();
        return fresh;
    }

    private void clamp() {
        frameQuality = Math.clamp(frameQuality, 20, 100);
        maxFps = Math.clamp(maxFps, 5, 120);
        frameFormat = "png".equalsIgnoreCase(frameFormat == null ? "" : frameFormat.trim()) ? "png" : "jpeg";
        maxFrameWidth = maxFrameWidth <= 0 ? 0 : Math.clamp(maxFrameWidth, 320, 7680);
        startupTimeoutSeconds = Math.clamp(startupTimeoutSeconds, 5, 180);
        idleShutdownMinutes = Math.clamp(idleShutdownMinutes, 1, 1440);
        zoom = Math.clamp(zoom, 0.25D, 4.0D);
        winWidth = Math.max(0, winWidth);
        winHeight = Math.max(0, winHeight);
        pinWidth = Math.clamp(pinWidth, 120, 3840);
        pinHeight = Math.clamp(pinHeight, 80, 2160);
        pinOpacity = Math.clamp(pinOpacity, 0.15D, 1.0D);
        if (homePage == null || homePage.isBlank()) {
            homePage = new Config().homePage;
        }
        if (searchTemplate == null || !searchTemplate.contains("%s")) {
            searchTemplate = new Config().searchTemplate;
        }
        if (browserPath == null) {
            browserPath = "";
        }
        if (lastPage == null) {
            lastPage = "";
        }
    }

    public void save() {
        clamp();
        try {
            Path path = PocketBrowser.configFile();
            Files.createDirectories(path.getParent());
            AtomicFile.replace(path, GSON.toJson(this));
        } catch (IOException e) {
            PocketBrowser.LOG.warn("Could not save the config", e);
        }
    }
}
