package com.pulseplayer.mobile;

import java.net.URI;
import java.util.Locale;
import org.json.JSONObject;

final class Track {
    final String uri;
    String title;
    String author = "";
    String artwork = "";
    boolean favorite;
    long downloadId;

    Track(String uri, String title) {
        this.uri = uri;
        this.title = title;
    }

    static Track read(JSONObject json) {
        Track track = new Track(json.optString("uri"), json.optString("title"));
        track.artwork = json.optString("artwork");
        track.author = json.optString("author");
        track.favorite = json.optBoolean("favorite");
        track.downloadId = json.optLong("downloadId");
        return track;
    }

    JSONObject write() throws org.json.JSONException {
        return new JSONObject().put("uri", uri).put("title", title)
            .put("author", author).put("artwork", artwork).put("favorite", favorite).put("downloadId", downloadId);
    }

    static boolean https(String url) {
        try {
            URI parsed = URI.create(url);
            return "https".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null
                && parsed.getUserInfo() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static String provider(String url) {
        if (!https(url)) return "";
        String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
        if (host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com")
            || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")) return "YouTube";
        if (host.equals("snd.sc") || host.equals("soundcloud.com") || host.endsWith(".soundcloud.com")) return "SoundCloud";
        return "";
    }

    static boolean downloadable(String url) {
        if (!https(url) || !provider(url).isEmpty()) return false;
        return URI.create(url).getPath().toLowerCase(Locale.ROOT)
            .matches(".*\\.(mp3|m4a|aac|ogg|opus|wav|flac)$");
    }
}
