package com.pulseplayer.mobile;

import java.util.*;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.*;

public final class StreamResolverSmoke {
    private static AudioStream audio(String id, MediaFormat format, DeliveryMethod delivery) {
        return new AudioStream.Builder().setId(id).setContent("https://example.com/" + id, true)
            .setMediaFormat(format).setDeliveryMethod(delivery).setAverageBitrate(128).build();
    }

    private static VideoStream video(String id, boolean videoOnly) {
        return new VideoStream.Builder().setId(id).setContent("https://example.com/" + id, true)
            .setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(videoOnly).setResolution("360p").build();
    }

    private static void expect(List<String> actual, String... ids) {
        List<String> expected = new ArrayList<>();
        for (String id : ids) expected.add("https://example.com/" + id);
        if (!expected.equals(actual)) throw new AssertionError(actual);
    }

    public static void main(String[] args) throws Exception {
        List<VideoStream> videos = Arrays.asList(video("video", true), video("muxed", false));
        expect(StreamResolver.candidates(Arrays.asList(
            audio("hls", MediaFormat.M4A, DeliveryMethod.HLS),
            audio("webm", MediaFormat.WEBMA, DeliveryMethod.PROGRESSIVE_HTTP),
            audio("m4a", MediaFormat.M4A, DeliveryMethod.PROGRESSIVE_HTTP)), videos),
            "m4a", "webm", "hls", "muxed");
        expect(StreamResolver.candidates(Collections.emptyList(), videos), "muxed");
        expect(StreamResolver.candidates(Collections.emptyList(), videos.subList(0, 1)));
        AudioStream playlist = audio("manifest", MediaFormat.M4A, DeliveryMethod.HLS);
        AudioStream file = audio("file", MediaFormat.MP3, DeliveryMethod.PROGRESSIVE_HTTP);
        StreamResolver.DownloadSource download = StreamResolver.downloadableAudio(Arrays.asList(playlist, file));
        if (download == null || !".mp3".equals(download.extension)
                || !"https://example.com/file".equals(download.url))
            throw new AssertionError("Offline download must select a standalone audio file");
        if (StreamResolver.downloadableAudio(Collections.singletonList(playlist)) != null)
            throw new AssertionError("HLS manifest must not be downloaded as a song");
        System.out.println("Passed: audio order, muxed fallback, video-only rejection, offline audio and HLS rejection");
        if (args.length > 0) {
            if ("search".equals(args[0])) {
                int size = StreamResolver.search(args[1], args[2]).size();
                if (size == 0) throw new AssertionError("Search returned no streams");
                System.out.println("Live search results: " + size);
            } else {
                StreamResolver.Result result = StreamResolver.resolve(args[0]);
                System.out.println("Resolved " + result.title + " (" + result.urls.size() + " candidates)");
                for (String url : result.urls) {
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                        new java.net.URL(url).openConnection();
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(7000);
                    connection.setRequestProperty("User-Agent", StreamResolver.AGENT);
                    connection.setRequestProperty("Range", "bytes=0-255");
                    try {
                        int status = connection.getResponseCode();
                        System.out.println("Stream response: " + status + " " + connection.getContentType());
                        if ((status == 200 || status == 206) && connection.getInputStream().read() != -1) return;
                    } catch (java.io.IOException error) {
                        System.out.println(error.getClass().getSimpleName());
                    } finally { connection.disconnect(); }
                }
                throw new AssertionError("No live candidate returned media bytes");
            }
        }
    }
}
