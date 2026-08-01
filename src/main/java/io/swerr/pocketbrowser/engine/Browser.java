package io.swerr.pocketbrowser.engine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.engine.cdp.CdpClient;
import io.swerr.pocketbrowser.engine.cdp.CdpJson;
import io.swerr.pocketbrowser.render.PageTexture;
import io.swerr.pocketbrowser.util.Urls;
import org.jetbrains.annotations.Nullable;

public final class Browser {
    public enum Status {
        IDLE,
        STARTING,
        READY,
        FAILED
    }

    public static final int MAX_TABS = 12;

    private static final long STREAM_LINGER_NANOS = 1_500_000_000L;

    private static final Browser INSTANCE = new Browser();

    private final ExecutorService bootstrap = Executors.newSingleThreadExecutor(runnable ->
            daemon(runnable, "PocketBrowser bootstrap"));
    private final ExecutorService decoder = decoderPool();

    private static ExecutorService decoderPool() {
        int threads = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 3);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads,
                30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> daemon(runnable, "PocketBrowser frames"));
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private final List<BrowserTab> tabs = new CopyOnWriteArrayList<>();
    private final Set<String> knownTargets = ConcurrentHashMap.newKeySet();
    private final Queue<PageTexture> graveyard = new ConcurrentLinkedQueue<>();

    private final AtomicInteger generation = new AtomicInteger();

    private volatile Status status = Status.IDLE;
    private volatile String failureKey = "";
    private volatile String failureDetail = "";
    private volatile String browserName = "";
    private volatile int activeIndex;
    private volatile long lastUseNanos = System.nanoTime();
    private volatile boolean signInRunning;
    private long lastMediaPollNanos;
    private volatile long lastDrawNanos;

    @Nullable
    private volatile ChromeProcess process;
    @Nullable
    private volatile CdpClient cdp;

    private Browser() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::killProcess, "PocketBrowser cleanup"));
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    public static Browser get() {
        return INSTANCE;
    }

    public Status status() {
        return status;
    }

    public String failureKey() {
        return failureKey;
    }

    public String failureDetail() {
        return failureDetail;
    }

    public String browserName() {
        return browserName;
    }

    public List<BrowserTab> tabs() {
        return tabs;
    }

    public int activeIndex() {
        return activeIndex;
    }

    @Nullable
    public BrowserTab activeTab() {
        List<BrowserTab> snapshot = tabs;
        int index = activeIndex;
        return index >= 0 && index < snapshot.size() ? snapshot.get(index) : null;
    }

    Executor decoder() {
        return decoder;
    }

    int frameQuality() {
        return Config.get().frameQuality;
    }

    String frameFormat() {
        return Config.get().frameFormat;
    }

    long frameIntervalNanos() {
        return 1_000_000_000L / Math.max(1, Config.get().maxFps);
    }

    public void touch() {
        lastUseNanos = System.nanoTime();
    }

    public synchronized void start() {
        touch();
        if (status == Status.STARTING || status == Status.READY || signInRunning) {
            return;
        }
        status = Status.STARTING;
        failureKey = "";
        failureDetail = "";
        bootstrap.execute(this::launch);
    }

    public synchronized void restart() {
        shutdown();
        status = Status.IDLE;
        start();
    }

    private void launch() {
        Config config = Config.get();
        int mine = generation.get();
        ChromeProcess started = null;
        try {
            ChromeFinder.Found found = ChromeFinder.find(config.browserPath).orElse(null);
            if (found == null) {
                fail("pocketbrowser.error.no_browser", "");
                return;
            }
            browserName = found.name();

            java.util.Optional<java.net.URI> existing =
                    ChromeProcess.liveEndpoint(PocketBrowser.profileDir());
            java.net.URI endpoint;
            if (existing.isPresent()) {
                endpoint = existing.get();
                PocketBrowser.LOG.info("Reusing the browser already listening on {}", endpoint);
            } else {
                started = ChromeProcess.start(found, config);
                endpoint = started.endpoint();
            }

            CdpClient client = CdpClient
                    .connect(endpoint, Duration.ofSeconds(config.startupTimeoutSeconds))
                    .join();

            boolean stale;
            synchronized (this) {
                stale = generation.get() != mine;
                if (!stale) {
                    client.onEvent(this::onEvent);
                    client.onDisconnect(this::onConnectionLost);

                    client.fire(null, "Target.setDiscoverTargets", CdpJson.obj("discover", true));
                    this.process = started;
                    this.cdp = client;
                }
            }
            if (stale) {
                client.close();
                started.close();
                return;
            }

            BrowserTab first = firstTab(client, startUrl(config)).join();
            if (generation.get() != mine) {
                return;
            }
            knownTargets.add(first.targetId());
            tabs.add(first);
            activeIndex = 0;
            first.setActive(true);

            touch();
            noteDrawn();
            status = Status.READY;
            PocketBrowser.LOG.info("PocketBrowser is ready on {}", browserName);
        } catch (Throwable error) {
            if (generation.get() != mine) {
                if (started != null) {
                    started.close();
                }
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            PocketBrowser.LOG.error("PocketBrowser could not start", cause);
            String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            if (started != null) {
                String log = started.recentLog(6);
                if (!log.isBlank()) {
                    detail = detail + "\n" + log;
                }
                started.close();
            }
            this.process = null;
            fail("pocketbrowser.error.launch", detail);
        }
    }

    private static String startUrl(Config config) {
        if (config.restoreLastPage && !config.lastPage.isBlank()) {
            return config.lastPage;
        }
        return config.homePage;
    }

    private CompletableFuture<BrowserTab> firstTab(CdpClient client, String url) {
        return client.call(null, "Target.getTargets", new JsonObject())
                .thenCompose(result -> {
                    JsonArray infos = array(result, "targetInfos");
                    for (JsonElement element : infos) {
                        JsonObject info = element.getAsJsonObject();
                        if ("page".equals(CdpJson.string(info, "type", ""))) {
                            return BrowserTab.adopt(this, client, CdpJson.string(info, "targetId", ""), url);
                        }
                    }
                    return BrowserTab.create(this, client, url);
                });
    }

    private void fail(String key, String detail) {
        failureKey = key;
        failureDetail = detail;
        status = Status.FAILED;
    }

    private void onConnectionLost() {
        if (status == Status.READY) {
            fail("pocketbrowser.error.lost", "");
        }
        for (BrowserTab tab : tabs) {
            tab.disposeAfterRemoteClose();
        }
        tabs.clear();
        knownTargets.clear();
    }

    public void shutdown() {
        teardown(false);
    }

    public void shutdownAndWait() {
        teardown(true);
    }

    private void teardown(boolean waitForIt) {
        CdpClient client;
        ChromeProcess running;
        synchronized (this) {
            generation.incrementAndGet();
            client = cdp;
            running = process;
            cdp = null;
            process = null;
            for (BrowserTab tab : tabs) {
                tab.disposeAfterRemoteClose();
            }
            tabs.clear();
            knownTargets.clear();
            activeIndex = 0;
            lastDrawNanos = 0L;
            if (status != Status.FAILED) {
                status = Status.IDLE;
            }
        }

        Runnable close = () -> {
            if (client != null) {
                if (running == null) {
                    client.fire(null, "Browser.close", new JsonObject());
                    try {
                        Thread.sleep(400L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                client.close();
            }
            if (running != null) {
                running.close();
            }
        };
        if (waitForIt) {
            close.run();
        } else {
            bootstrap.execute(close);
        }
    }

    private void killProcess() {
        ChromeProcess running = process;
        process = null;
        if (running != null) {
            running.close();
        }
    }

    public void newTab(String url) {
        newTab(url, true);
    }

    public void newTab(String url, boolean focus) {
        CdpClient client = cdp;
        if (client == null || status != Status.READY || tabs.size() >= MAX_TABS) {
            return;
        }
        touch();
        BrowserTab.create(this, client, url)
                .thenAccept(tab -> {
                    knownTargets.add(tab.targetId());
                    tabs.add(tab);
                    if (focus) {
                        select(tabs.size() - 1);
                    }
                })
                .exceptionally(error -> {
                    PocketBrowser.LOG.warn("Could not open a tab", error);
                    return null;
                });
    }

    public boolean signInRunning() {
        return signInRunning;
    }

    public void startSignIn(@Nullable String url) {
        if (signInRunning) {
            return;
        }
        signInRunning = true;
        String target = signInTarget(url);
        bootstrap.execute(() -> {
            try {
                teardown(true);
                ChromeProcess.awaitProfileFree(PocketBrowser.profileDir(), 6000L);
                ChromeFinder.Found found = ChromeFinder.find(Config.get().browserPath).orElse(null);
                if (found == null) {
                    fail("pocketbrowser.error.no_browser", "");
                    return;
                }
                Process window = ChromeProcess.startSignInWindow(found, PocketBrowser.profileDir(), target);

                if (window.waitFor(2, TimeUnit.SECONDS)) {
                    PocketBrowser.LOG.warn("The sign-in window exited immediately - the profile is "
                            + "still held by another browser process");
                    fail("pocketbrowser.error.signin_busy", "");
                    return;
                }

                PocketBrowser.LOG.info("Sign-in window open; waiting for the player to close it");
                window.waitFor();

                Thread.sleep(2000L);
                PocketBrowser.LOG.info("Sign-in window closed; the session is now in the profile");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                PocketBrowser.LOG.warn("Could not open a sign-in window", e);
                fail("pocketbrowser.error.launch", String.valueOf(e.getMessage()));
            } finally {
                signInRunning = false;
            }
        });
    }

    private static String signInTarget(@Nullable String url) {
        boolean blank = url == null || url.isBlank() || "about:blank".equals(url);
        String host = blank ? "" : Urls.host(url).toLowerCase(java.util.Locale.ROOT);

        if (host.endsWith("google.com") || host.endsWith("youtube.com") || host.endsWith("youtu.be")) {
            return "https://accounts.google.com/ServiceLogin?continue="
                    + URLEncoder.encode(url, StandardCharsets.UTF_8);
        }
        if (host.endsWith("twitch.tv")) {
            return "https://www.twitch.tv/login";
        }
        if (host.endsWith("github.com")) {
            return "https://github.com/login";
        }
        if (host.endsWith("reddit.com")) {
            return "https://www.reddit.com/login";
        }

        return blank ? "https://accounts.google.com/" : url;
    }

    public void restartStream() {
        BrowserTab tab = activeTab();
        if (tab != null) {
            tab.restartStream();
        }
    }

    public void select(int index) {
        List<BrowserTab> snapshot = tabs;
        if (index < 0 || index >= snapshot.size()) {
            return;
        }
        touch();
        activeIndex = index;
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).setActive(i == index);
        }
    }

    private static int afterRemoving(int removed, int wasActive, int size) {
        if (removed < wasActive) {
            return Math.clamp(wasActive - 1, 0, size - 1);
        }
        if (removed > wasActive) {
            return Math.clamp(wasActive, 0, size - 1);
        }
        return Math.clamp(removed, 0, size - 1);
    }

    public void closeTab(BrowserTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) {
            return;
        }
        int wasActive = activeIndex;
        tabs.remove(index);
        knownTargets.remove(tab.targetId());
        tab.close();

        if (tabs.isEmpty()) {
            activeIndex = 0;
            newTab(Config.get().homePage);
            return;
        }
        select(afterRemoving(index, wasActive, tabs.size()));
    }

    private void onEvent(@Nullable String sessionId, String method, JsonObject params) {
        if (sessionId == null) {
            onBrowserEvent(method, params);
            return;
        }
        for (BrowserTab tab : tabs) {
            if (sessionId.equals(tab.sessionId())) {
                tab.handleEvent(method, params);
                return;
            }
        }
    }

    private void onBrowserEvent(String method, JsonObject params) {
        switch (method) {
            case "Target.targetInfoChanged" -> {
                JsonObject info = CdpJson.child(params, "targetInfo");
                String id = CdpJson.string(info, "targetId", "");
                for (BrowserTab tab : tabs) {
                    if (tab.targetId().equals(id)) {
                        tab.updateInfo(info);
                        return;
                    }
                }
            }
            case "Target.targetCreated" -> {
                JsonObject info = CdpJson.child(params, "targetInfo");
                String id = CdpJson.string(info, "targetId", "");
                boolean popup = info.has("openerId");

                if (popup && "page".equals(CdpJson.string(info, "type", "")) && knownTargets.add(id)) {
                    adoptPopup(id);
                }
            }
            case "Target.targetDestroyed" -> {
                String id = CdpJson.string(params, "targetId", "");
                for (BrowserTab tab : tabs) {
                    if (tab.targetId().equals(id)) {
                        removeClosedTab(tab);
                        return;
                    }
                }
            }
            default -> {
            }
        }
    }

    private void adoptPopup(String targetId) {
        CdpClient client = cdp;
        if (client == null) {
            return;
        }
        if (tabs.size() >= MAX_TABS) {
            knownTargets.remove(targetId);
            client.fire(null, "Target.closeTarget", CdpJson.obj("targetId", targetId));
            return;
        }
        BrowserTab.adopt(this, client, targetId, null)
                .thenAccept(tab -> {
                    tabs.add(tab);
                    select(tabs.size() - 1);
                })
                .exceptionally(error -> {
                    knownTargets.remove(targetId);
                    return null;
                });
    }

    private void removeClosedTab(BrowserTab tab) {
        int index = tabs.indexOf(tab);
        int wasActive = activeIndex;
        tabs.remove(tab);
        knownTargets.remove(tab.targetId());
        tab.disposeAfterRemoteClose();
        if (tabs.isEmpty()) {
            activeIndex = 0;
        } else {
            select(afterRemoving(Math.max(0, index), wasActive, tabs.size()));
        }
    }

    void retire(PageTexture texture) {
        graveyard.add(texture);
    }

    public void tick(boolean inUse) {
        PageTexture dead;
        while ((dead = graveyard.poll()) != null) {
            dead.close();
        }

        if (inUse) {
            touch();
            pollMedia();
            BrowserTab tab = activeTab();
            if (tab != null) {
                tab.refineWhenStill();
            }
        }
        if (status == Status.READY) {
            followDrawing();
        }

        ChromeProcess running = process;
        if (status == Status.READY && running != null && !running.isAlive()) {
            PocketBrowser.LOG.warn("The browser process exited on its own");
            shutdown();
            fail("pocketbrowser.error.lost", "");
            return;
        }

        if (status == Status.READY && !inUse) {
            long idleMinutes = (System.nanoTime() - lastUseNanos) / 60_000_000_000L;
            if (idleMinutes >= Config.get().idleShutdownMinutes) {
                PocketBrowser.LOG.info("Shutting the browser down after {} idle minutes", idleMinutes);
                rememberLastPage();
                shutdown();
            }
        }
    }

    public void noteDrawn() {
        lastDrawNanos = System.nanoTime();
    }

    private void followDrawing() {
        BrowserTab tab = activeTab();
        if (tab == null) {
            return;
        }
        long drawn = lastDrawNanos;
        boolean wanted = drawn != 0L && System.nanoTime() - drawn < STREAM_LINGER_NANOS;
        if (tab.isActive() != wanted) {
            tab.setActive(wanted);
        }
    }

    private void pollMedia() {
        if (status != Status.READY) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastMediaPollNanos < 500_000_000L) {
            return;
        }
        lastMediaPollNanos = now;
        BrowserTab tab = activeTab();
        if (tab != null) {
            tab.pollMedia();
        }
    }

    public void rememberLastPage() {
        BrowserTab tab = activeTab();
        Config config = Config.get();
        if (tab != null && config.restoreLastPage) {
            String url = tab.url();
            if (!url.isBlank() && !url.equals("about:blank") && !url.equals(config.lastPage)) {
                config.lastPage = url;
                config.save();
            }
        }
    }

    private static JsonArray array(JsonObject source, String key) {
        if (source.has(key) && source.get(key).isJsonArray()) {
            return source.getAsJsonArray(key);
        }
        return new JsonArray();
    }
}
