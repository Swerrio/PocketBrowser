package io.swerr.pocketbrowser.engine.cdp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class CdpJson {
    private CdpJson() {
    }

    public static JsonObject obj(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("CdpJson.obj needs an even number of arguments");
        }
        JsonObject out = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object value = keyValues[i + 1];
            switch (value) {
                case null -> {
                }
                case String s -> out.addProperty(key, s);
                case Number n -> out.addProperty(key, n);
                case Boolean b -> out.addProperty(key, b);
                case JsonElement e -> out.add(key, e);
                default -> out.addProperty(key, String.valueOf(value));
            }
        }
        return out;
    }

    public static String string(JsonObject source, String key, String fallback) {
        if (source == null || !source.has(key) || source.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = source.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public static int integer(JsonObject source, String key, int fallback) {
        if (source == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return source.get(key).getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean bool(JsonObject source, String key, boolean fallback) {
        if (source == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return source.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static JsonObject child(JsonObject source, String key) {
        if (source != null && source.has(key) && source.get(key).isJsonObject()) {
            return source.getAsJsonObject(key);
        }
        return new JsonObject();
    }
}
