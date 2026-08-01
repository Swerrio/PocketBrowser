package io.swerr.pocketbrowser.data;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.swerr.pocketbrowser.PocketBrowser;
import io.swerr.pocketbrowser.util.AtomicFile;
import io.swerr.pocketbrowser.util.Urls;

public final class BrowsingData {
    public record Entry(String url, String title, long time) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Entry>>() {
    }.getType();

    private static final int HISTORY_LIMIT = 400;
    private static final long WRITE_GAP_NANOS = 1_000_000_000L;

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PocketBrowser library");
        thread.setDaemon(true);
        return thread;
    });

    private static final Store BOOKMARK_STORE = new Store("bookmarks.json");
    private static final Store HISTORY_STORE = new Store("history.json");

    private static List<Entry> bookmarks;
    private static List<Entry> history;

    private BrowsingData() {
    }

    private static final class Store {
        private final String name;
        private final AtomicReference<List<Entry>> pending = new AtomicReference<>();
        private final AtomicBoolean queued = new AtomicBoolean();
        private long lastWriteNanos;

        private Store(String name) {
            this.name = name;
        }

        private void save(List<Entry> entries) {
            pending.set(List.copyOf(entries));
            if (queued.compareAndSet(false, true)) {
                WRITER.execute(this::drain);
            }
        }

        private void drain() {
            List<Entry> snapshot;
            while ((snapshot = pending.getAndSet(null)) != null) {
                long wait = lastWriteNanos + WRITE_GAP_NANOS - System.nanoTime();
                if (wait > 0L) {
                    try {
                        Thread.sleep(wait / 1_000_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        pending.compareAndSet(null, snapshot);
                        break;
                    }
                    List<Entry> newer = pending.getAndSet(null);
                    if (newer != null) {
                        snapshot = newer;
                    }
                }
                write(file(name), snapshot);
                lastWriteNanos = System.nanoTime();
            }
            queued.set(false);
            if (pending.get() != null && queued.compareAndSet(false, true)) {
                WRITER.execute(this::drain);
            }
        }

        private void flush() {
            List<Entry> snapshot = pending.getAndSet(null);
            if (snapshot != null) {
                write(file(name), snapshot);
                lastWriteNanos = System.nanoTime();
            }
        }
    }

    public static void flush() {
        try {
            WRITER.submit(() -> {
                BOOKMARK_STORE.flush();
                HISTORY_STORE.flush();
            }).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            PocketBrowser.LOG.warn("Could not finish saving bookmarks and history", e);
        }
    }

    public static List<Entry> bookmarks() {
        if (bookmarks == null) {
            bookmarks = read(file("bookmarks.json"));
        }
        return bookmarks;
    }

    public static boolean isBookmarked(String url) {
        return bookmarks().stream().anyMatch(entry -> entry.url().equals(url));
    }

    public static boolean toggleBookmark(String url, String title) {
        if (Urls.isBlank(url)) {
            return false;
        }
        List<Entry> list = bookmarks();
        boolean removed = list.removeIf(entry -> entry.url().equals(url));
        if (!removed) {
            list.add(new Entry(url, title == null || title.isBlank() ? Urls.host(url) : title,
                    System.currentTimeMillis()));
        }
        BOOKMARK_STORE.save(list);
        return !removed;
    }

    public static List<Entry> history() {
        if (history == null) {
            history = read(file("history.json"));
        }
        return history;
    }

    private static final String[] NEVER_RECORD = {
            "accounts.google.com", "/login", "/signin", "/sign-in", "/oauth", "/auth/",
            "access_token", "id_token", "token=", "session=", "sessionid", "auth_key",
            "api_key", "apikey", "secret", "password",
    };

    public static void recordVisit(String url, String title) {
        if (Urls.isBlank(url) || !worthRecording(url)) {
            return;
        }
        String stored = withoutDataFragment(url);
        List<Entry> list = history();
        list.removeIf(entry -> entry.url().equals(stored));
        list.add(0, new Entry(stored, title == null ? "" : title, System.currentTimeMillis()));
        while (list.size() > HISTORY_LIMIT) {
            list.remove(list.size() - 1);
        }
        HISTORY_STORE.save(list);
    }

    private static boolean worthRecording(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http")) {
            return false;
        }
        for (String marker : NEVER_RECORD) {
            if (lower.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    private static String withoutDataFragment(String url) {
        int hash = url.indexOf('#');
        if (hash < 0) {
            return url;
        }
        String fragment = url.substring(hash + 1);
        return fragment.indexOf('=') >= 0 || fragment.indexOf('&') >= 0 ? url.substring(0, hash) : url;
    }

    public static void clearHistory() {
        history().clear();
        HISTORY_STORE.save(history);
    }

    public static List<Entry> suggest(String typed, int limit) {
        String needle = typed.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        if (needle.isEmpty()) {
            return out;
        }
        for (Entry entry : bookmarks()) {
            if (matches(entry, needle)) {
                out.add(entry);
            }
            if (out.size() >= limit) {
                return out;
            }
        }
        for (Entry entry : history()) {
            if (matches(entry, needle) && out.stream().noneMatch(had -> had.url().equals(entry.url()))) {
                out.add(entry);
            }
            if (out.size() >= limit) {
                return out;
            }
        }
        return out;
    }

    private static boolean matches(Entry entry, String lowerNeedle) {
        return entry.url().toLowerCase(Locale.ROOT).contains(lowerNeedle)
                || entry.title().toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static Path file(String name) {
        return PocketBrowser.dataDir().resolve(name);
    }

    private static List<Entry> read(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                List<Entry> loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LIST_TYPE);
                if (loaded != null) {
                    loaded.removeIf(entry -> entry == null || entry.url() == null || entry.url().isBlank());
                    return loaded;
                }
            } catch (Exception e) {
                PocketBrowser.LOG.warn("Could not read {}", path, e);
            }
        }
        return new ArrayList<>();
    }

    private static void write(Path path, List<Entry> entries) {
        try {
            Files.createDirectories(path.getParent());
            AtomicFile.replace(path, GSON.toJson(entries));
        } catch (IOException e) {
            PocketBrowser.LOG.warn("Could not save {}", path, e);
        }
    }
}
