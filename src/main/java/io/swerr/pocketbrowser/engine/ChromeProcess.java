package io.swerr.pocketbrowser.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.config.Config;

public final class ChromeProcess implements AutoCloseable {
    private static final String PORT_FILE = "DevToolsActivePort";

    private final Process process;
    private final URI endpoint;
    private final Path logFile;

    private ChromeProcess(Process process, URI endpoint, Path logFile) {
        this.process = process;
        this.endpoint = endpoint;
        this.logFile = logFile;
    }

    public URI endpoint() {
        return endpoint;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public static Optional<URI> liveEndpoint(Path profile) {
        Path portFile = profile.resolve(PORT_FILE);
        if (!Files.isRegularFile(portFile)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(portFile, StandardCharsets.UTF_8);
            if (lines.size() < 2 || lines.get(0).isBlank() || lines.get(1).isBlank()) {
                return Optional.empty();
            }
            int port = Integer.parseInt(lines.get(0).trim());
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", port), 400);
            }
            return Optional.of(URI.create("ws://127.0.0.1:" + port + lines.get(1).trim()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static ChromeProcess start(ChromeFinder.Found browser, Config config) throws IOException {
        try {
            return startOnce(browser, config);
        } catch (IOException first) {
            PocketBrowser.LOG.info("The browser did not come up ({}); retrying once", first.getMessage());
            try {
                Thread.sleep(2500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return startOnce(browser, config);
        }
    }

    private static ChromeProcess startOnce(ChromeFinder.Found browser, Config config) throws IOException {
        Path profile = PocketBrowser.profileDir();
        Files.createDirectories(profile);
        sweep(profile);

        Path portFile = profile.resolve(PORT_FILE);
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException e) {
            PocketBrowser.LOG.warn("Could not clear {} ({}); starting anyway", portFile, e.getMessage());
        }

        Path logFile = PocketBrowser.dataDir().resolve("browser.log");
        Files.createDirectories(logFile.getParent());

        ProcessBuilder builder = new ProcessBuilder(command(browser, config, profile));
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());
        Process process = builder.start();

        URI endpoint;
        try {
            endpoint = awaitEndpoint(process, portFile, config.startupTimeoutSeconds);
        } catch (IOException | RuntimeException e) {
            process.destroy();
            throw e;
        }

        PocketBrowser.LOG.info("{} is up on {}", browser.name(), endpoint);
        return new ChromeProcess(process, endpoint, logFile);
    }

    private static final List<String> DISPOSABLE = List.of(
            "Cache", "Code Cache", "GPUCache", "GrShaderCache", "ShaderCache", "DawnCache",
            "DawnWebGPUCache", "DawnGraphiteCache", "GPUPersistentCache", "Service Worker",
            "Shared Dictionary", "optimization_guide_model_store", "Crashpad",
            "component_crx_cache", "extensions_crx_cache", "BrowserMetrics");

    static void sweep(Path profile) {
        long freed = 0L;
        for (Path victim : disposable(profile)) {
            freed += delete(victim);
        }
        if (freed >= 1L << 20) {
            PocketBrowser.LOG.info("Cleared {} MB of browser caches", freed >> 20);
        }
    }

    private static List<Path> disposable(Path profile) {
        List<Path> victims = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(profile, 3)) {
            walk.filter(Files::isDirectory)
                    .filter(path -> DISPOSABLE.contains(path.getFileName().toString()))
                    .forEach(victims::add);
        } catch (IOException e) {
            PocketBrowser.LOG.debug("Could not list the profile", e);
        }
        return victims;
    }

    private static long delete(Path root) {
        long[] freed = {0L};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    try {
                        Files.delete(file);
                        freed[0] += attributes.size();
                    } catch (IOException ignored) {
                        return FileVisitResult.CONTINUE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException e) {
                    try {
                        Files.deleteIfExists(directory);
                    } catch (IOException ignored) {
                        return FileVisitResult.CONTINUE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            PocketBrowser.LOG.debug("Could not clear {}", root, e);
        }
        return freed[0];
    }

    public static Process startSignInWindow(ChromeFinder.Found browser, Path profile, String url)
            throws IOException {
        Files.createDirectories(profile);
        ProcessBuilder builder = new ProcessBuilder(
                browser.path().toString(),
                "--user-data-dir=" + profile.toAbsolutePath(),
                "--no-first-run",
                "--no-default-browser-check",

                "--window-position=120,80",
                "--window-size=1100,780",
                "--new-window",
                url);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    public static void awaitProfileFree(Path profile, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        boolean free = false;
        while (System.nanoTime() < deadline) {
            if (liveEndpoint(profile).isEmpty()) {
                free = true;
                break;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!free) {
            PocketBrowser.LOG.warn("The profile still looks busy after {}ms; opening anyway", timeoutMillis);
        }
        try {
            Files.deleteIfExists(profile.resolve(PORT_FILE));
        } catch (IOException e) {
            PocketBrowser.LOG.debug("Could not clear the stale port file", e);
        }
    }

    private static List<String> command(ChromeFinder.Found browser, Config config, Path profile) {
        List<String> command = new ArrayList<>();
        command.add(browser.path().toString());

        command.add("--remote-debugging-port=0");
        command.add("--remote-allow-origins=*");
        command.add("--user-data-dir=" + profile.toAbsolutePath());

        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--no-service-autorun");
        command.add("--noerrdialogs");
        command.add("--disable-breakpad");
        command.add("--disable-component-update");
        command.add("--disable-default-apps");
        command.add("--disable-hang-monitor");
        command.add("--disable-prompt-on-repost");
        command.add("--disable-sync");
        command.add("--disable-client-side-phishing-detection");
        command.add("--disable-background-networking");
        command.add("--metrics-recording-only");
        command.add("--hide-crash-restore-bubble");
        command.add("--password-store=basic");
        command.add("--use-mock-keychain");
        command.add("--disable-features=Translate,MediaRouter,OptimizationHints,"
                + "AcceptCHFrame,CalculateNativeWinOcclusion,InterestFeedContentSuggestions,"
                + "OptimizationGuideModelDownloading,OptimizationTargetPrediction,"
                + "OptimizationHintsFetching");

        command.add("--disable-gpu-shader-disk-cache");
        command.add("--disk-cache-size=" + (64L << 20));
        command.add("--media-cache-size=" + (32L << 20));

        command.add("--disable-background-timer-throttling");
        command.add("--disable-backgrounding-occluded-windows");
        command.add("--disable-renderer-backgrounding");

        command.add("--window-size=1920,1080");

        if (config.headless) {
            command.add("--headless=new");
        } else {
            command.add("--window-position=-32000,-32000");
        }

        command.add("about:blank");
        return command;
    }

    private static URI awaitEndpoint(Process process, Path portFile, int timeoutSeconds) throws IOException {
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("The browser exited immediately with code " + process.exitValue());
            }
            if (Files.isRegularFile(portFile)) {
                List<String> lines = Files.readAllLines(portFile, StandardCharsets.UTF_8);
                if (lines.size() >= 2 && !lines.get(0).isBlank() && !lines.get(1).isBlank()) {
                    try {
                        int port = Integer.parseInt(lines.get(0).trim());
                        return URI.create("ws://127.0.0.1:" + port + lines.get(1).trim());
                    } catch (NumberFormatException e) {
                        PocketBrowser.LOG.debug("Ignoring a partially written {}", PORT_FILE);
                    }
                }
            }
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the browser to start");
            }
        }
        throw new IOException("The browser did not open a DevTools port within " + timeoutSeconds + "s");
    }

    public String recentLog(int maxLines) {
        try {
            if (!Files.isRegularFile(logFile)) {
                return "";
            }
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - maxLines), lines.size()));
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    @Override
    public void close() {
        if (!process.isAlive()) {
            return;
        }

        List<ProcessHandle> children = process.descendants().toList();

        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        for (ProcessHandle child : children) {
            if (child.isAlive()) {
                child.destroy();
            }
        }
        for (ProcessHandle child : children) {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }
}
