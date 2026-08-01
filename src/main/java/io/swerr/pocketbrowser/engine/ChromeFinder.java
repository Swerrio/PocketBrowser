package io.swerr.pocketbrowser.engine;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.swerr.pocketbrowser.PocketBrowser;

public final class ChromeFinder {
    public record Found(Path path, String name) {
    }

    private ChromeFinder() {
    }

    public static List<Found> findAll() {
        List<Found> out = new ArrayList<>();
        for (Path candidate : candidates()) {
            if (Files.isRegularFile(candidate) && out.stream().noneMatch(f -> f.path().equals(candidate))) {
                out.add(new Found(candidate, prettyName(candidate)));
            }
        }
        return out;
    }

    public static Optional<Found> find(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            try {
                Path explicit = Path.of(configuredPath.trim());
                if (Files.isRegularFile(explicit)) {
                    return Optional.of(new Found(explicit, prettyName(explicit)));
                }
                PocketBrowser.LOG.warn("browserPath points at {} which is not a file - searching instead", explicit);
            } catch (InvalidPathException e) {
                PocketBrowser.LOG.warn("browserPath is not a valid path: {}", configuredPath);
            }
        }

        for (Path candidate : candidates()) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(new Found(candidate, prettyName(candidate)));
            }
        }
        return Optional.empty();
    }

    private static String prettyName(Path path) {
        String file = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String full = path.toString().toLowerCase(Locale.ROOT);
        if (file.startsWith("msedge") || full.contains("microsoft edge")) {
            return "Microsoft Edge";
        }
        if (file.startsWith("brave")) {
            return "Brave";
        }
        if (file.startsWith("vivaldi")) {
            return "Vivaldi";
        }
        if (full.contains("chromium")) {
            return "Chromium";
        }
        if (file.startsWith("chrome") || full.contains("google chrome")) {
            return "Google Chrome";
        }
        return path.getFileName().toString();
    }

    private static List<Path> candidates() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> out = new ArrayList<>();
        if (os.contains("win")) {
            windows(out);
        } else if (os.contains("mac") || os.contains("darwin")) {
            macos(out);
        } else {
            linux(out);
        }
        return out;
    }

    private static void windows(List<Path> out) {
        String local = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        addAll(out, "Google\\Chrome\\Application\\chrome.exe", local, programFiles, programFilesX86);
        addAll(out, "Microsoft\\Edge\\Application\\msedge.exe", programFiles, programFilesX86, local);
        addAll(out, "BraveSoftware\\Brave-Browser\\Application\\brave.exe", programFiles, programFilesX86, local);
        addAll(out, "Vivaldi\\Application\\vivaldi.exe", local, programFiles, programFilesX86);
        addAll(out, "Chromium\\Application\\chrome.exe", local, programFiles, programFilesX86);
        addAll(out, "Google\\Chrome Beta\\Application\\chrome.exe", local, programFiles, programFilesX86);
        addAll(out, "Google\\Chrome Dev\\Application\\chrome.exe", local, programFiles, programFilesX86);
        addAll(out, "Google\\Chrome SxS\\Application\\chrome.exe", local, programFiles, programFilesX86);
    }

    private static void addAll(List<Path> out, String relative, String... roots) {
        for (String root : roots) {
            if (root != null && !root.isBlank()) {
                out.add(Path.of(root, relative));
            }
        }
    }

    private static void macos(List<Path> out) {
        String home = System.getProperty("user.home", "");
        String[] apps = {
                "Google Chrome.app/Contents/MacOS/Google Chrome",
                "Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "Brave Browser.app/Contents/MacOS/Brave Browser",
                "Vivaldi.app/Contents/MacOS/Vivaldi",
                "Chromium.app/Contents/MacOS/Chromium",
        };
        for (String app : apps) {
            out.add(Path.of("/Applications", app));
            if (!home.isBlank()) {
                out.add(Path.of(home, "Applications", app));
            }
        }
    }

    private static void linux(List<Path> out) {
        String[] names = {
                "google-chrome-stable", "google-chrome", "chromium", "chromium-browser",
                "microsoft-edge-stable", "microsoft-edge", "brave-browser", "vivaldi-stable",
        };
        String[] dirs = {"/usr/bin", "/usr/local/bin", "/snap/bin", "/opt/google/chrome", "/opt/microsoft/msedge"};
        for (String name : names) {
            for (String dir : dirs) {
                out.add(Path.of(dir, name));
            }
        }

        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(":")) {
                if (entry.isBlank()) {
                    continue;
                }
                for (String name : names) {
                    try {
                        out.add(Path.of(entry, name));
                    } catch (InvalidPathException ignored) {
                    }
                }
            }
        }
    }
}
