package io.swerr.pocketbrowser.engine.cdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swerr.pocketbrowser.PocketBrowser;

public final class CdpClient implements AutoCloseable {
    @FunctionalInterface
    public interface EventSink {
        void accept(String sessionId, String method, JsonObject params);
    }

    private static final Gson GSON = new Gson();
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final List<EventSink> sinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> disconnectHandlers = new CopyOnWriteArrayList<>();

    private volatile WebSocket socket;
    private volatile boolean closed;
    private CompletableFuture<WebSocket> sendChain;

    private CdpClient(HttpClient http) {
        this.http = http;
    }

    public static CompletableFuture<CdpClient> connect(URI uri, Duration connectTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        CdpClient client = new CdpClient(http);
        return http.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(uri, new Reader(client))
                .thenApply(webSocket -> {
                    client.attach(webSocket);
                    return client;
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        http.shutdownNow();
                    }
                });
    }

    private synchronized void attach(WebSocket webSocket) {
        this.socket = webSocket;
        this.sendChain = CompletableFuture.completedFuture(webSocket);
    }

    public void onEvent(EventSink sink) {
        sinks.add(sink);
    }

    public void onDisconnect(Runnable handler) {
        disconnectHandlers.add(handler);
    }

    public boolean isClosed() {
        return closed;
    }

    public CompletableFuture<JsonObject> call(String sessionId, String method, JsonObject params) {
        if (closed) {
            return CompletableFuture.failedFuture(new CdpException(method + ": connection is closed"));
        }
        int id = ids.incrementAndGet();
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        pending.put(id, result);
        result.orTimeout(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .whenComplete((value, error) -> pending.remove(id));
        write(id, sessionId, method, params);
        return result;
    }

    public void fire(String sessionId, String method, JsonObject params) {
        if (!closed) {
            write(ids.incrementAndGet(), sessionId, method, params);
        }
    }

    private void write(int id, String sessionId, String method, JsonObject params) {
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("method", method);
        if (params != null) {
            message.add("params", params);
        }
        if (sessionId != null) {
            message.addProperty("sessionId", sessionId);
        }
        send(GSON.toJson(message));
    }

    private synchronized void send(String text) {
        if (closed || sendChain == null) {
            return;
        }
        sendChain = sendChain
                .thenCompose(webSocket -> webSocket.sendText(text, true))
                .exceptionally(error -> {
                    if (!closed) {
                        PocketBrowser.LOG.debug("CDP send failed", error);
                    }
                    return socket;
                });
    }

    private static final String FRAME_EVENT = "\"Page.screencastFrame\"";
    private static final String DATA_KEY = "\"data\":\"";
    private static final String SESSION_KEY = "\"sessionId\":";
    private static final String SESSION_TEXT_KEY = "\"sessionId\":\"";
    private static final int HEAD = 160;

    private void dispatch(String message) {
        if (message.length() > HEAD && message.lastIndexOf(FRAME_EVENT, HEAD) >= 0 && dispatchFrame(message)) {
            return;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(message).getAsJsonObject();
        } catch (RuntimeException e) {
            PocketBrowser.LOG.warn("Unreadable CDP message ({} chars)", message.length());
            return;
        }

        if (root.has("id")) {
            CompletableFuture<JsonObject> waiting = pending.remove(root.get("id").getAsInt());
            if (waiting == null) {
                return;
            }
            if (root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                waiting.completeExceptionally(new CdpException(CdpJson.string(error, "message", "unknown error")));
            } else {
                waiting.complete(CdpJson.child(root, "result"));
            }
            return;
        }

        if (!root.has("method")) {
            return;
        }
        String method = root.get("method").getAsString();
        String sessionId = CdpJson.string(root, "sessionId", null);
        JsonObject params = CdpJson.child(root, "params");
        for (EventSink sink : sinks) {
            try {
                sink.accept(sessionId, method, params);
            } catch (RuntimeException e) {
                PocketBrowser.LOG.warn("CDP event handler for {} failed", method, e);
            }
        }
    }

    private boolean dispatchFrame(String message) {
        int at = message.indexOf(DATA_KEY);
        if (at < 0) {
            return false;
        }
        int from = at + DATA_KEY.length();
        int end = message.indexOf('"', from);
        if (end < 0) {
            return false;
        }

        String data = message.substring(from, end);
        String session = quotedValue(message, SESSION_TEXT_KEY, end);
        int ack = numberValue(message, SESSION_KEY, end);
        if (session == null || ack < 0) {
            return false;
        }

        JsonObject params = new JsonObject();
        params.addProperty("data", data);
        params.addProperty("sessionId", ack);
        for (EventSink sink : sinks) {
            try {
                sink.accept(session, "Page.screencastFrame", params);
            } catch (RuntimeException e) {
                PocketBrowser.LOG.warn("CDP frame handler failed", e);
            }
        }
        return true;
    }

    private static String quotedValue(String message, String key, int start) {
        int at = message.indexOf(key, start);
        if (at < 0) {
            return null;
        }
        int from = at + key.length();
        int end = message.indexOf('"', from);
        return end < 0 ? null : message.substring(from, end);
    }

    private static int numberValue(String message, String key, int start) {
        for (int at = message.indexOf(key, start); at >= 0; at = message.indexOf(key, at + 1)) {
            int from = at + key.length();
            int end = from;
            while (end < message.length() && Character.isDigit(message.charAt(end))) {
                end++;
            }
            if (end > from && end - from < 10) {
                return Integer.parseInt(message, from, end, 10);
            }
        }
        return -1;
    }

    private void terminate(Throwable cause) {
        if (closed) {
            return;
        }
        closed = true;
        CdpException failure = new CdpException("DevTools connection lost", cause);
        pending.values().forEach(future -> future.completeExceptionally(failure));
        pending.clear();
        for (Runnable handler : disconnectHandlers) {
            try {
                handler.run();
            } catch (RuntimeException e) {
                PocketBrowser.LOG.warn("Disconnect handler failed", e);
            }
        }
        http.shutdownNow();
    }

    @Override
    public void close() {
        WebSocket webSocket = socket;
        closed = true;
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (RuntimeException e) {
                webSocket.abort();
            }
        }
        pending.values().forEach(future ->
                future.completeExceptionally(new CdpException("DevTools connection closed")));
        pending.clear();

        http.shutdownNow();
    }

    private static final class Reader implements WebSocket.Listener {
        private final CdpClient owner;
        private final StringBuilder buffer = new StringBuilder(1 << 16);

        private Reader(CdpClient owner) {
            this.owner = owner;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                if (buffer.capacity() > (1 << 22)) {
                    buffer.trimToSize();
                }
                try {
                    owner.dispatch(message);
                } catch (RuntimeException e) {
                    PocketBrowser.LOG.warn("Failed to handle a CDP message", e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            owner.terminate(null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            owner.terminate(error);
        }
    }
}
