package com.pulseplayer.mobile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class SoundCloudBuffer {
    static final long LIMIT = 512L * 1024 * 1024;
    private static final Pattern MAP = Pattern.compile("(?:^|,)URI=\"([^\"]+)\"");

    // For clear, on-demand fragmented MP4, join the initialization and media
    // fragments into one temporary file before passing it to Android MediaPlayer.
    static File prepare(String source, File cache) throws IOException {
        URI uri = URI.create(source);
        if (!uri.getPath().endsWith(".m3u8")) return null;
        ByteArrayOutputStream manifest = new ByteArrayOutputStream();
        copy(uri, manifest, 1024 * 1024);
        List<URI> parts = fragments(uri, new String(manifest.toByteArray(), StandardCharsets.UTF_8));
        if (parts.isEmpty()) return null;
        long available = cache.getUsableSpace();
        if (available < 32L * 1024 * 1024) throw new IOException("SoundCloud buffer: insufficient free storage");
        File file = File.createTempFile("pulse-sc-", ".m4a", cache);
        boolean complete = false;
        try {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                long remaining = Math.min(LIMIT, available - 16L * 1024 * 1024);
                for (URI part : parts) remaining -= copy(part, out, remaining);
            }
            complete = true;
            return file;
        } finally {
            if (!complete) file.delete();
        }
    }

    static List<URI> fragments(URI source, String manifest) throws IOException {
        if (!manifest.startsWith("#EXTM3U") || !manifest.contains("#EXT-X-MAP:")) return Collections.emptyList();
        if (!manifest.contains("#EXT-X-ENDLIST") || manifest.contains("#EXT-X-KEY:")
            || manifest.contains("#EXT-X-BYTERANGE") || manifest.contains("#EXT-X-DISCONTINUITY"))
            return Collections.emptyList();
        List<URI> parts = new ArrayList<>();
        for (String line : manifest.split("\\r?\\n")) {
            line = line.trim();
            if (line.startsWith("#EXT-X-MAP:")) {
                Matcher init = MAP.matcher(line.substring("#EXT-X-MAP:".length()));
                if (!parts.isEmpty() || !init.find() || line.contains("BYTERANGE=")) return Collections.emptyList();
                parts.add(source.resolve(init.group(1)));
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                if (parts.isEmpty()) return Collections.emptyList();
                parts.add(source.resolve(line));
            }
            if (parts.size() > 16000) throw new IOException("SoundCloud track has too many fragments");
        }
        return parts.size() > 1 ? parts : Collections.emptyList();
    }

    private static long copy(URI uri, OutputStream out, long limit) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) throw new IOException("Expected HTTPS");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(9000);
        connection.setRequestProperty("User-Agent", StreamResolver.AGENT);
        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[32768];
            long total = 0;
            for (int size; (size = in.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                total += size;
                if (total > limit) throw new IOException("SoundCloud playback buffer exceeded limit");
                out.write(buffer, 0, size);
            }
            return total;
        } finally {
            connection.disconnect();
        }
    }
}
