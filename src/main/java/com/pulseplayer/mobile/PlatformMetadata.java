package com.pulseplayer.mobile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

final class PlatformMetadata {
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    interface Result { void accept(String title, String author); }

    static void lookup(String source, Result result) {
        executor.execute(() -> {
            String title = "";
            String author = "";
            try {
                String endpoint = "YouTube".equals(Track.provider(source))
                    ? "https://www.youtube.com/oembed?format=json&url="
                    : "https://soundcloud.com/oembed?format=json&url=";
                HttpURLConnection connection = (HttpURLConnection) new URL(
                    endpoint + PlatformLinks.encode(source)).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                try (InputStream stream = connection.getInputStream()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        if (bytes.size() + read > 262144) throw new java.io.IOException("Metadata too large");
                        bytes.write(buffer, 0, read);
                    }
                    JSONObject data = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    title = data.optString("title").trim();
                    author = data.optString("author_name").trim();
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { }
            result.accept(title, author);
        });
    }
}
