package com.pulseplayer.mobile;

import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public final class SoundCloudSmoke {
    public static void main(String[] args) throws Exception {
        String html = "<script id='__NEXT_DATA__' type='application/json'>"
            + "{\"runtimeConfig\":{\"clientId\":\"0123456789abcdefghijklmnopqrstuv\"},"
            + "\"props\":{\"pageProps\":{\"trackUrn\":\"soundcloud:tracks:1\","
            + "\"initialStoreState\":{\"entities\":{\"tracks\":{\"soundcloud:tracks:1\":{\"data\":{"
            + "\"title\":\"Example\",\"policy\":\"ALLOW\",\"track_authorization\":\"a+b\","
            + "\"media\":{\"transcodings\":[" + format("aac_96k", "hls") + ","
            + format("aac_160k", "ctr-encrypted-hls") + "]}}}}}}}}}</script>";
        AtomicInteger fetched = new AtomicInteger();
        SoundCloudAudio track = SoundCloudAudio.readMobilePage(html, url -> {
            if (!url.contains("aac_96k") || !url.contains("track_authorization=a%2Bb"))
                throw new AssertionError("Unexpected transcoding request");
            fetched.incrementAndGet();
            return new Response(200, "OK", Collections.emptyMap(),
                "{\"url\":\"https://example.com/audio.m3u8\"}", url);
        });
        if (fetched.get() != 1 || track.streams.size() != 1
                || track.streams.get(0).getAverageBitrate() != 96
                || track.streams.get(0).getDeliveryMethod() != DeliveryMethod.HLS)
            throw new AssertionError("Expected one unencrypted AAC 96k HLS candidate");
        System.out.println("SoundCloud 96k-only and encrypted-track fixture passed");
        java.net.URI manifest = java.net.URI.create("https://example.com/audio/playlist.m3u8");
        String clear = "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4,\nsegment.m4s\n#EXT-X-ENDLIST";
        java.util.List<java.net.URI> parts = SoundCloudBuffer.fragments(manifest, clear);
        if (parts.size() != 2 || !parts.get(0).equals(manifest.resolve("init.mp4"))
                || !parts.get(1).equals(manifest.resolve("segment.m4s"))
                || !SoundCloudBuffer.fragments(manifest, clear + "\n#EXT-X-KEY:METHOD=AES-128").isEmpty())
            throw new AssertionError("Unexpected HLS fragment plan");
        System.out.println("SoundCloud HLS init and fragment ordering passed");
        if (args.length == 3) {
            String page = new String(Files.readAllBytes(Paths.get(args[0])), java.nio.charset.StandardCharsets.UTF_8);
            SoundCloudAudio sample = SoundCloudAudio.readMobilePage(page, url -> {
                String file = url.contains("aac_160k") ? args[1] : args[2];
                return new Response(200, "OK", Collections.emptyMap(),
                    new String(Files.readAllBytes(Paths.get(file)), java.nio.charset.StandardCharsets.UTF_8), url);
            });
            if (sample.streams.size() != 2) throw new AssertionError("Expected two live AAC candidates");
            System.out.println("Live SoundCloud response parsed: " + sample.title + ", two AAC candidates");
        }
    }

    private static String format(String preset, String protocol) {
        return "{\"preset\":\"" + preset + "\",\"url\":\"https://api-mobi.soundcloud.com/media/" + preset
            + "\",\"format\":{\"protocol\":\"" + protocol + "\",\"mime_type\":\"audio/mp4\"}}";
    }
}
