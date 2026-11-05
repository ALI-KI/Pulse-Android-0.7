package com.pulseplayer.mobile;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

final class PlatformLinks {
    private static final List<String> SC_PAGES = Arrays.asList(
        "discover", "search", "stream", "settings", "you", "upload", "charts",
        "terms-of-use", "pages", "stations", "groups", "messages");

    static String encode(String value) {
        try { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }

    static String youtubeId(String url) {
        if (!"YouTube".equals(Track.provider(url))) return "";
        URI uri = URI.create(url);
        String path = uri.getPath();
        String id = "";
        if ("youtu.be".equalsIgnoreCase(uri.getHost())) id = path.substring(Math.min(1, path.length()));
        else if (path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/")) {
            id = path.substring(path.lastIndexOf('/') + 1);
        } else if ("/watch".equals(path) && uri.getRawQuery() != null) {
            for (String part : uri.getRawQuery().split("&")) {
                if (part.startsWith("v=")) { id = part.substring(2); break; }
            }
        }
        return id.matches("[A-Za-z0-9_-]{11}") ? id : "";
    }

    static String media(String url) {
        String id = youtubeId(url);
        if (!id.isEmpty()) return "https://www.youtube.com/watch?v=" + id;
        if (!"SoundCloud".equals(Track.provider(url))) return "";
        URI uri = URI.create(url);
        String path = uri.getRawPath();
        if (path == null || path.length() < 2) return "";
        if ("on.soundcloud.com".equalsIgnoreCase(uri.getHost())
            || "snd.sc".equalsIgnoreCase(uri.getHost())) return "https://" + uri.getHost() + path;
        String[] parts = path.substring(1).split("/");
        if (SC_PAGES.contains(parts[0])) return "";
        if (parts.length == 2 && !Arrays.asList("tracks", "albums", "sets", "reposts",
            "likes", "followers", "following", "popular-tracks").contains(parts[1])) {
            return "https://soundcloud.com" + path;
        }
        if (parts.length == 3 && "sets".equals(parts[1])) return "https://soundcloud.com" + path;
        return "";
    }
}
