package io.swerr.pocketbrowser.engine;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.NativeImage;
import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.config.Config;
import io.swerr.pocketbrowser.engine.cdp.CdpClient;
import io.swerr.pocketbrowser.engine.cdp.CdpKeys;
import io.swerr.pocketbrowser.engine.cdp.CdpJson;
import io.swerr.pocketbrowser.render.FrameDecoder;
import io.swerr.pocketbrowser.render.PageTexture;
import org.jetbrains.annotations.Nullable;

public final class BrowserTab implements AutoCloseable {
    private static final AtomicBoolean DECODE_FAILURE_REPORTED = new AtomicBoolean();

    private final Browser browser;
    private final CdpClient cdp;
    private final String targetId;
    private final String sessionId;
    private final PageTexture texture = new PageTexture();

    private static final long LOADING_PATIENCE_NANOS = 20_000_000_000L;

    private volatile String url = "";
    private volatile String title = "";
    private volatile boolean loading;
    private volatile long loadingSinceNanos;

    @Nullable
    private volatile String mainFrameId;
    private volatile boolean canGoBack;
    private volatile boolean canGoForward;
    private volatile boolean closed;
    private volatile boolean active;
    private volatile boolean streaming;

    @Nullable
    private volatile Viewport viewport;

    private volatile int expectedFrameWidth;
    private volatile long lastViewportResetNanos;
    private volatile String zoomScript = "";
    private final AtomicInteger zoomGeneration = new AtomicInteger();

    private static final long SETTLE_NANOS = 140_000_000L;
    private static final long STILL_FLOOR_NANOS = 600_000_000L;

    private volatile long lastFrameNanos;
    private volatile long frameCount;
    private volatile int lastFrameWidth;
    private volatile boolean stillRequested;
    private volatile long lastStillNanos;
    private volatile String qualityApplied = "";
    private volatile int qualityTries;

    private final Object ackLock = new Object();
    private final AtomicLong uploads = new AtomicLong();
    private long nextAckNanos;

    private BrowserTab(Browser browser, CdpClient cdp, String targetId, String sessionId) {
        this.browser = browser;
        this.cdp = cdp;
        this.targetId = targetId;
        this.sessionId = sessionId;
    }

    static CompletableFuture<BrowserTab> create(Browser browser, CdpClient cdp, String url) {
        return cdp.call(null, "Target.createTarget", CdpJson.obj("url", "about:blank"))
                .thenCompose(result -> adopt(browser, cdp, CdpJson.string(result, "targetId", ""), url));
    }

    static CompletableFuture<BrowserTab> adopt(Browser browser, CdpClient cdp, String targetId,
                                               @Nullable String navigateTo) {
        return cdp.call(null, "Target.attachToTarget", CdpJson.obj("targetId", targetId, "flatten", true))
                .thenApply(result -> {
                    BrowserTab tab = new BrowserTab(browser, cdp, targetId, CdpJson.string(result, "sessionId", ""));
                    tab.enableDomains();
                    if (navigateTo != null && !navigateTo.isBlank()) {
                        tab.navigate(navigateTo);
                    }
                    return tab;
                });
    }

    private void enableDomains() {
        cdp.fire(sessionId, "Page.enable", new JsonObject());
        cdp.fire(sessionId, "Runtime.enable", new JsonObject());
        if (Config.get().bestVideoQuality) {
            cdp.fire(sessionId, "Page.addScriptToEvaluateOnNewDocument",
                    CdpJson.obj("source", SITE_PREFERENCES));
        }
    }

    public String targetId() {
        return targetId;
    }

    String sessionId() {
        return sessionId;
    }

    public String url() {
        return url;
    }

    public String title() {
        String current = title;
        if (!current.isBlank()) {
            return current;
        }
        return url.isBlank() ? "" : url;
    }

    public boolean loading() {
        return loading && System.nanoTime() - loadingSinceNanos < LOADING_PATIENCE_NANOS;
    }

    private void setLoading(boolean value) {
        if (value && !loading) {
            loadingSinceNanos = System.nanoTime();
        }
        loading = value;
    }

    private boolean isMainFrame(JsonObject params) {
        String frameId = CdpJson.string(params, "frameId", "");
        String main = mainFrameId;
        return main == null || main.isEmpty() || main.equals(frameId);
    }

    public boolean canGoBack() {
        return canGoBack;
    }

    public boolean canGoForward() {
        return canGoForward;
    }

    public PageTexture texture() {
        return texture;
    }

    void handleEvent(String method, JsonObject params) {
        switch (method) {
            case "Page.screencastFrame" -> onFrame(params);
            case "Page.frameStartedLoading" -> {
                if (isMainFrame(params)) {
                    setLoading(true);
                }
            }
            case "Page.frameStoppedLoading" -> {
                if (isMainFrame(params)) {
                    setLoading(false);
                    refreshHistory();
                }
            }
            case "Page.loadEventFired" -> {
                setLoading(false);
                refreshHistory();
            }
            case "Page.frameNavigated" -> {
                JsonObject frame = CdpJson.child(params, "frame");
                if (!frame.has("parentId")) {
                    mainFrameId = CdpJson.string(frame, "id", mainFrameId);
                    url = CdpJson.string(frame, "url", url);
                }
                refreshHistory();
            }
            case "Page.navigatedWithinDocument" -> {
                url = CdpJson.string(params, "url", url);
                setLoading(false);
                refreshHistory();
            }
            case "Page.javascriptDialogOpening" ->
                    cdp.fire(sessionId, "Page.handleJavaScriptDialog", CdpJson.obj("accept", true));
            default -> {
            }
        }
    }

    void updateInfo(JsonObject info) {
        title = CdpJson.string(info, "title", title);
        String reported = CdpJson.string(info, "url", url);
        if (!reported.isBlank()) {
            url = reported;
        }
    }

    private void onFrame(JsonObject params) {
        String data = CdpJson.string(params, "data", null);
        int ackId = CdpJson.integer(params, "sessionId", -1);
        if (data == null || closed) {
            return;
        }
        lastFrameNanos = System.nanoTime();
        frameCount++;
        stillRequested = false;
        long sequence = uploads.incrementAndGet();
        browser.decoder().execute(() -> {
            long due = reserveAck();
            boolean acked = due - System.nanoTime() <= 0L;
            if (acked) {
                fireAck(ackId);
            }
            try {
                NativeImage image = FrameDecoder.decode(Base64.getDecoder().decode(data));
                noteFrameSize(image.getWidth());
                texture.offer(image, sequence);
            } catch (Exception e) {
                if (DECODE_FAILURE_REPORTED.compareAndSet(false, true)) {
                    PocketBrowser.LOG.error("Could not decode a browser frame - the page will not "
                            + "update. Further failures will not be logged.", e);
                }
            } finally {
                if (!acked) {
                    settle(due);
                    fireAck(ackId);
                }
            }
        });
    }

    private long reserveAck() {
        synchronized (ackLock) {
            long interval = browser.frameIntervalNanos();
            long due = Math.max(System.nanoTime() - interval, nextAckNanos);
            nextAckNanos = due + interval;
            return due;
        }
    }

    private static void settle(long due) {
        long wait = due - System.nanoTime();
        if (wait < 2_000_000L) {
            return;
        }
        try {
            Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fireAck(int ackId) {
        if (ackId >= 0 && !closed) {
            cdp.fire(sessionId, "Page.screencastFrameAck", CdpJson.obj("sessionId", ackId));
        }
    }

    private void noteFrameSize(int width) {
        lastFrameWidth = width;
        int expected = expectedFrameWidth;
        if (expected <= 0 || Math.abs(width - expected) <= 2) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastViewportResetNanos < 1_000_000_000L) {
            return;
        }
        lastViewportResetNanos = now;
        viewport = null;
    }

    private void refreshHistory() {
        cdp.call(sessionId, "Page.getNavigationHistory", new JsonObject())
                .thenAccept(result -> {
                    int index = CdpJson.integer(result, "currentIndex", 0);
                    JsonArray entries = result.has("entries") && result.get("entries").isJsonArray()
                            ? result.getAsJsonArray("entries")
                            : new JsonArray();
                    canGoBack = index > 0;
                    canGoForward = index < entries.size() - 1;
                })
                .exceptionally(error -> null);
    }

    public void navigate(String target) {
        url = target;
        setLoading(true);
        cdp.call(sessionId, "Page.navigate", CdpJson.obj("url", target)).exceptionally(error -> null);
    }

    public void back() {
        history(-1);
    }

    public void forward() {
        history(1);
    }

    private void history(int delta) {
        cdp.call(sessionId, "Page.getNavigationHistory", new JsonObject())
                .thenAccept(result -> {
                    int index = CdpJson.integer(result, "currentIndex", 0) + delta;
                    JsonArray entries = result.has("entries") && result.get("entries").isJsonArray()
                            ? result.getAsJsonArray("entries")
                            : new JsonArray();
                    if (index < 0 || index >= entries.size()) {
                        return;
                    }
                    JsonObject entry = entries.get(index).getAsJsonObject();
                    setLoading(true);
                    cdp.fire(sessionId, "Page.navigateToHistoryEntry",
                            CdpJson.obj("entryId", CdpJson.integer(entry, "id", 0)));
                })
                .exceptionally(error -> null);
    }

    public void reload(boolean ignoreCache) {
        setLoading(true);
        cdp.fire(sessionId, "Page.reload", CdpJson.obj("ignoreCache", ignoreCache));
    }

    public void stopLoading() {
        setLoading(false);
        cdp.fire(sessionId, "Page.stopLoading", new JsonObject());
    }

    public record Media(boolean present, boolean paused, double position, double duration, boolean muted) {
        public static final Media NONE = new Media(false, true, 0.0D, 0.0D, false);
    }

    private static final String PICK_MEDIA =
            "(()=>{const l=[...document.querySelectorAll('video,audio')];"
                    + "return l.find(m=>!m.paused)||l.sort((a,b)=>"
                    + "(b.clientWidth*b.clientHeight)-(a.clientWidth*a.clientHeight))[0]||null})()";

    private static final String MEDIA_PROBE =
            "(()=>{const v=" + PICK_MEDIA + ";if(!v)return '';"
                    + "return JSON.stringify({p:v.paused,t:v.currentTime||0,"
                    + "d:isFinite(v.duration)?v.duration:0,m:!!v.muted,"
                    + "a:!v.paused||v.currentTime>0})})()";

    private volatile Media media = Media.NONE;

    public Media media() {
        return media;
    }

    void pollMedia() {
        if (closed || !active) {
            return;
        }
        cdp.call(sessionId, "Runtime.evaluate", CdpJson.obj(
                        "expression", MEDIA_PROBE, "returnByValue", true))
                .thenAccept(result -> {
                    media = parseMedia(result);
                    if (media.present()) {
                        applyBestVideoQuality();
                    }
                })
                .exceptionally(error -> null);
    }

    private static final String ZOOM_SETTER =
            "(()=>{const z='$';const a=()=>{const e=document.documentElement;if(e)e.style.zoom=z};"
                    + "a();document.addEventListener('DOMContentLoaded',a);"
                    + "document.addEventListener('readystatechange',a)})()";

    private static final String QUALITY_SETTER =
            "(()=>{const m={highres:4320,hd2160:2160,hd1440:1440,hd1080:1080,hd720:720,"
                    + "large:480,medium:360,small:240,tiny:144};"
                    + "const p=document.getElementById('movie_player');"
                    + "if(p&&p.getAvailableQualityLevels&&p.setPlaybackQualityRange){"
                    + "const v=document.querySelector('video');"
                    + "const box=v?v.getBoundingClientRect().height:0;"
                    + "const want=box>40?Math.round(box):$;"
                    + "const have=p.getAvailableQualityLevels().filter(q=>m[q]);"
                    + "if(have.length){const fit=have.filter(q=>m[q]>=want);"
                    + "const pick=fit.length?fit[fit.length-1]:have[0];"
                    + "p.setPlaybackQualityRange(pick,pick);"
                    + "if(p.setPlaybackQuality)p.setPlaybackQuality(pick)}}})()";

    private static final String SITE_PREFERENCES =
            "(()=>{try{if(location.hostname.endsWith('twitch.tv'))"
                    + "localStorage.setItem('video-quality',JSON.stringify({default:'chunked'}))}"
                    + "catch(e){}})()";

    private static final int QUALITY_ATTEMPTS = 6;

    private void applyBestVideoQuality() {
        Viewport current = viewport;
        if (closed || current == null || !Config.get().bestVideoQuality) {
            return;
        }
        String key = url + "|" + current.frameHeight();
        if (!key.equals(qualityApplied)) {
            qualityApplied = key;
            qualityTries = 0;
        }
        if (qualityTries >= QUALITY_ATTEMPTS) {
            return;
        }
        qualityTries++;
        cdp.fire(sessionId, "Runtime.evaluate", CdpJson.obj(
                "expression", QUALITY_SETTER.replace("$", String.valueOf(current.frameHeight()))));
    }

    void refineWhenStill() {
        Config config = Config.get();
        Viewport current = viewport;
        if (closed || !active || !streaming || current == null
                || stillRequested || !config.sharpenWhenStill || "png".equals(config.frameFormat)) {
            return;
        }
        Media state = media;
        if (state.present() && !state.paused()) {
            return;
        }
        if (lastFrameWidth != current.frameWidth()) {
            return;
        }
        long since = lastFrameNanos;
        long now = System.nanoTime();
        if (since == 0L || now - since < SETTLE_NANOS || now - lastStillNanos < STILL_FLOOR_NANOS) {
            return;
        }

        stillRequested = true;
        lastStillNanos = now;
        long mark = frameCount;
        cdp.call(sessionId, "Page.captureScreenshot",
                        CdpJson.obj("format", "png", "captureBeyondViewport", false))
                .thenAccept(result -> acceptStill(result, mark, current))
                .exceptionally(error -> null);
    }

    private void acceptStill(JsonObject result, long mark, Viewport when) {
        String data = CdpJson.string(result, "data", null);
        if (data == null || closed || frameCount != mark || !when.matches(viewport)) {
            return;
        }
        long sequence = uploads.incrementAndGet();
        browser.decoder().execute(() -> {
            if (closed || frameCount != mark) {
                return;
            }
            try {
                NativeImage image = FrameDecoder.decode(Base64.getDecoder().decode(data));
                if (image.getWidth() == when.frameWidth() && frameCount == mark) {
                    texture.offer(image, sequence);
                } else {
                    image.close();
                }
            } catch (Exception e) {
                PocketBrowser.LOG.debug("Could not decode a lossless still", e);
            }
        });
    }

    private static Media parseMedia(JsonObject result) {
        String value = CdpJson.string(CdpJson.child(result, "result"), "value", "");
        if (value == null || value.isBlank()) {
            return Media.NONE;
        }
        try {
            JsonObject state = JsonParser.parseString(value).getAsJsonObject();
            return new Media(CdpJson.bool(state, "a", true),
                    CdpJson.bool(state, "p", true),
                    state.get("t").getAsDouble(),
                    state.get("d").getAsDouble(),
                    CdpJson.bool(state, "m", false));
        } catch (RuntimeException e) {
            return Media.NONE;
        }
    }

    public void mediaTogglePlay() {
        runOnMedia("if(v.paused){v.play()}else{v.pause()}");
    }

    public void mediaSeek(double seconds) {
        runOnMedia("v.currentTime=Math.max(0,v.currentTime+(" + seconds + "))");
    }

    public void mediaSeekTo(double fraction) {
        runOnMedia("if(isFinite(v.duration))v.currentTime=v.duration*" + Math.clamp(fraction, 0.0D, 1.0D));
    }

    public void mediaToggleMute() {
        runOnMedia("v.muted=!v.muted");
    }

    private void runOnMedia(String body) {
        if (closed) {
            return;
        }
        cdp.fire(sessionId, "Runtime.evaluate", CdpJson.obj(
                "expression", "(()=>{const v=" + PICK_MEDIA + ";if(v){" + body + "}})()",
                "userGesture", true));
    }

    public CompletableFuture<String> linkAt(double cssX, double cssY) {
        if (closed) {
            return CompletableFuture.completedFuture("");
        }
        long x = Math.round(cssX);
        long y = Math.round(cssY);

        String expression = "(()=>{const X=" + x + ",Y=" + y + ";"
                + "const link=e=>{"
                + "while(e&&e.shadowRoot){const i=e.shadowRoot.elementFromPoint(X,Y);if(!i||i===e)break;e=i}"
                + "while(e){if(e.tagName==='A'&&e.href)return e.href;"
                + "const r=e.getRootNode();e=e.parentElement||(r&&r.host?r.host:null)}return ''};"
                + "const stack=document.elementsFromPoint?document.elementsFromPoint(X,Y)"
                + ":[document.elementFromPoint(X,Y)];"
                + "for(const el of (stack||[])){const h=link(el);if(h)return h}"
                + "return ''})()";
        return cdp.call(sessionId, "Runtime.evaluate", CdpJson.obj(
                        "expression", expression, "returnByValue", true))
                .thenApply(result -> CdpJson.string(CdpJson.child(result, "result"), "value", ""))
                .exceptionally(error -> "");
    }

    public void find(String needle, boolean forward) {
        if (closed || needle.isBlank()) {
            return;
        }

        String quoted = new JsonPrimitive(needle).toString();
        cdp.fire(sessionId, "Runtime.evaluate", CdpJson.obj(
                "expression", "window.find(" + quoted + ", false, " + !forward + ", true, false, true, false)",
                "returnByValue", true,
                "userGesture", true));
    }

    public CompletableFuture<String> selection() {
        return cdp.call(sessionId, "Runtime.evaluate", CdpJson.obj(
                        "expression", "String(window.getSelection())",
                        "returnByValue", true))
                .thenApply(result -> CdpJson.string(CdpJson.child(result, "result"), "value", ""))
                .exceptionally(error -> "");
    }

    boolean isActive() {
        return active;
    }

    public synchronized void setActive(boolean value) {
        if (closed || active == value) {
            return;
        }
        active = value;
        if (value) {
            startStream();
        } else {
            stopStream();
        }
    }

    private void startStream() {
        if (streaming || closed) {
            return;
        }
        streaming = true;
        cdp.fire(sessionId, "Page.startScreencast", CdpJson.obj(
                "format", browser.frameFormat(),
                "quality", browser.frameQuality(),
                "maxWidth", 4096,
                "maxHeight", 4096,
                "everyNthFrame", 1));
    }

    public synchronized void restartStream() {
        if (closed || !active) {
            return;
        }
        stopStream();
        startStream();
    }

    private void stopStream() {
        if (!streaming) {
            return;
        }
        streaming = false;
        cdp.fire(sessionId, "Page.stopScreencast", new JsonObject());
    }

    public void applyViewport(Viewport wanted) {
        Viewport previous = viewport;
        if (closed || wanted.matches(previous)) {
            return;
        }
        viewport = wanted;
        expectedFrameWidth = wanted.frameWidth();
        cdp.fire(sessionId, "Emulation.setDeviceMetricsOverride", CdpJson.obj(
                "width", wanted.frameWidth(),
                "height", wanted.frameHeight(),
                "deviceScaleFactor", 1,
                "screenWidth", wanted.frameWidth(),
                "screenHeight", wanted.frameHeight(),
                "mobile", false));
        if (previous == null || previous.zoom() != wanted.zoom()) {
            applyZoom(wanted.zoom());
        }
    }

    private void applyZoom(double zoom) {
        int mine = zoomGeneration.incrementAndGet();
        String previous = zoomScript;
        zoomScript = "";
        if (!previous.isEmpty()) {
            cdp.fire(sessionId, "Page.removeScriptToEvaluateOnNewDocument",
                    CdpJson.obj("identifier", previous));
        }

        String source = ZOOM_SETTER.replace("$", zoom == 1.0D ? "" : String.valueOf(zoom));
        cdp.fire(sessionId, "Runtime.evaluate", CdpJson.obj("expression", source));
        if (zoom == 1.0D) {
            return;
        }
        cdp.call(sessionId, "Page.addScriptToEvaluateOnNewDocument", CdpJson.obj("source", source))
                .thenAccept(result -> {
                    String identifier = CdpJson.string(result, "identifier", "");
                    if (identifier.isEmpty()) {
                        return;
                    }
                    if (zoomGeneration.get() == mine && !closed) {
                        zoomScript = identifier;
                    } else {
                        cdp.fire(sessionId, "Page.removeScriptToEvaluateOnNewDocument",
                                CdpJson.obj("identifier", identifier));
                    }
                })
                .exceptionally(error -> null);
    }

    @Nullable
    public Viewport viewport() {
        return viewport;
    }

    public void mouseMoved(double cssX, double cssY, int heldMask, int modifiers) {
        mouse("mouseMoved", cssX, cssY, "none", heldMask, 0, modifiers);
    }

    public void mousePressed(double cssX, double cssY, int button, int clickCount, int modifiers) {
        mouse("mousePressed", cssX, cssY, CdpKeys.mouseButton(button),
                CdpKeys.buttonMask(button), clickCount, modifiers);
    }

    public void mouseReleased(double cssX, double cssY, int button, int clickCount, int modifiers) {
        mouse("mouseReleased", cssX, cssY, CdpKeys.mouseButton(button), 0, clickCount, modifiers);
    }

    private void mouse(String type, double x, double y, String button, int buttons, int clicks, int modifiers) {
        if (closed) {
            return;
        }
        JsonObject params = CdpJson.obj(
                "type", type,
                "x", Math.round(x),
                "y", Math.round(y),
                "button", button,
                "buttons", buttons,
                "modifiers", modifiers);
        if (clicks > 0) {
            params.addProperty("clickCount", clicks);
        }
        cdp.fire(sessionId, "Input.dispatchMouseEvent", params);
    }

    public void scroll(double cssX, double cssY, double deltaX, double deltaY, int modifiers) {
        if (closed) {
            return;
        }
        cdp.fire(sessionId, "Input.dispatchMouseEvent", CdpJson.obj(
                "type", "mouseWheel",
                "x", Math.round(cssX),
                "y", Math.round(cssY),
                "deltaX", deltaX,
                "deltaY", deltaY,
                "modifiers", modifiers,
                "button", "none",
                "buttons", 0));
    }

    public void keyDown(CdpKeys.Key key, int modifiers) {
        if (closed) {
            return;
        }

        JsonObject params = CdpJson.obj(
                "type", key.text() == null ? "rawKeyDown" : "keyDown",
                "windowsVirtualKeyCode", key.windowsVirtualKeyCode(),
                "nativeVirtualKeyCode", key.windowsVirtualKeyCode(),
                "key", key.key(),
                "code", key.code(),
                "modifiers", modifiers);
        if (key.text() != null) {
            params.addProperty("text", key.text());
            params.addProperty("unmodifiedText", key.text());
        }
        cdp.fire(sessionId, "Input.dispatchKeyEvent", params);
    }

    public void keyUp(CdpKeys.Key key, int modifiers) {
        if (closed) {
            return;
        }
        cdp.fire(sessionId, "Input.dispatchKeyEvent", CdpJson.obj(
                "type", "keyUp",
                "windowsVirtualKeyCode", key.windowsVirtualKeyCode(),
                "nativeVirtualKeyCode", key.windowsVirtualKeyCode(),
                "key", key.key(),
                "code", key.code(),
                "modifiers", modifiers));
    }

    public void typed(String text) {
        if (closed || text.isEmpty()) {
            return;
        }
        cdp.fire(sessionId, "Input.dispatchKeyEvent", CdpJson.obj(
                "type", "char",
                "text", text,
                "unmodifiedText", text,
                "key", text));
    }

    public void insertText(String text) {
        if (closed || text.isEmpty()) {
            return;
        }
        cdp.fire(sessionId, "Input.insertText", CdpJson.obj("text", text));
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        streaming = false;
        browser.retire(texture);
        cdp.fire(null, "Target.closeTarget", CdpJson.obj("targetId", targetId));
    }

    void disposeAfterRemoteClose() {
        if (closed) {
            return;
        }
        closed = true;
        streaming = false;
        browser.retire(texture);
    }
}
