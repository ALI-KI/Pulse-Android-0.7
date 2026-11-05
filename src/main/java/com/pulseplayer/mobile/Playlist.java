package com.pulseplayer.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Ordered, device-local playlist. Tracks are referenced by their stable library URI. */
final class Playlist {
    final String id;
    String name;
    final List<String> uris = new ArrayList<>();

    Playlist(String name) { this(UUID.randomUUID().toString(), name); }
    private Playlist(String id, String name) { this.id = id; this.name = name; }

    static Playlist read(JSONObject json) throws JSONException {
        Playlist list = new Playlist(json.getString("id"), json.getString("name"));
        JSONArray entries = json.optJSONArray("uris");
        if (entries != null) for (int i = 0; i < entries.length(); i++) {
            String uri = entries.optString(i);
            if (!uri.isEmpty() && !list.uris.contains(uri)) list.uris.add(uri);
        }
        return list;
    }
    JSONObject write() throws JSONException {
        JSONArray entries = new JSONArray();
        for (String uri : uris) entries.put(uri);
        return new JSONObject().put("id", id).put("name", name).put("uris", entries);
    }
}
