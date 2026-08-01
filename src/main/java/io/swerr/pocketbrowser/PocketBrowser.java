package io.swerr.pocketbrowser;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PocketBrowser {
    public static final String MODID = "pocketbrowser";
    public static final Logger LOG = LoggerFactory.getLogger("PocketBrowser");

    private PocketBrowser() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static Path dataDir() {
        return FabricLoader.getInstance().getGameDir().resolve(MODID);
    }

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(MODID + ".json");
    }

    public static Path profileDir() {
        return dataDir().resolve("profile");
    }
}
