package io.swerr.pocketbrowser.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class Urls {
    private static final Set<String> ALLOWED = Set.of("http", "https", "file", "about", "data", "view-source");

    private Urls() {
    }

    public static String resolve(String typed, String searchTemplate) {
        String text = typed == null ? "" : typed.trim();
        if (text.isEmpty()) {
            return "about:blank";
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int scheme = lower.indexOf("://");
        if (scheme > 0 && ALLOWED.contains(lower.substring(0, scheme))) {
            return text;
        }
        if (lower.startsWith("about:") || lower.startsWith("data:") || lower.startsWith("view-source:")) {
            return text;
        }
        if (lower.startsWith("localhost") || lower.startsWith("127.0.0.1")) {
            return "http://" + text;
        }

        if (looksLikeHost(text)) {
            return "https://" + text;
        }
        return search(text, searchTemplate);
    }

    private static String search(String query, String searchTemplate) {
        return searchTemplate.replace("%s", URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    private static boolean looksLikeHost(String text) {
        if (text.indexOf(' ') >= 0 || text.indexOf('"') >= 0) {
            return false;
        }

        int slash = text.indexOf('/');
        String host = slash < 0 ? text : text.substring(0, slash);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        int dot = host.lastIndexOf('.');
        if (dot <= 0 || dot == host.length() - 1) {
            return false;
        }

        String tld = host.substring(dot + 1);
        return tld.length() >= 2 && tld.chars().allMatch(Character::isLetter);
    }

    public static String host(String url) {
        try {
            String authority = URI.create(url).getHost();
            if (authority == null || authority.isBlank()) {
                return url;
            }
            return authority.startsWith("www.") ? authority.substring(4) : authority;
        } catch (RuntimeException e) {
            return url;
        }
    }

    public static boolean isSecure(String url) {
        return url.toLowerCase(Locale.ROOT).startsWith("https://");
    }

    public static boolean isBlank(String url) {
        return url == null || url.isBlank() || url.equals("about:blank");
    }
}
