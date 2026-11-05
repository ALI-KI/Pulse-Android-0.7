package com.pulseplayer.mobile;

import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class SoundCloudSearchSmoke {
    public static void main(String[] args) throws Exception {
        String query = "البقرة";
        String mobile = Files.readString(Path.of(args[0]));
        String response = Files.readString(Path.of(args[1]));
        List<StreamInfoItem> tracks = SoundCloudAudio.search(query, url -> {
            if (!url.contains("q=" + PlatformLinks.encode(query)))
                throw new AssertionError("Arabic query was not preserved");
            if (url.startsWith("https://m.soundcloud.com/search/sounds?")) return reply(url, mobile, 200);
            if (!url.contains("client_id=")) throw new AssertionError("Missing mobile client ID");
            if (url.startsWith("https://api-mobi.soundcloud.com/search/tracks?")) return reply(url, "", 503);
            if (url.startsWith("https://api-v2.soundcloud.com/search/tracks?")) return reply(url, response, 200);
            throw new AssertionError("Unexpected search request");
        });
        if (tracks.size() < 10 || !tracks.get(0).getName().contains(query)
                || !"SoundCloud".equals(Track.provider(tracks.get(0).getUrl())))
            throw new AssertionError("Missing playable track links for Arabic search");
        System.out.println("Parsed " + tracks.size() + " live Arabic search results; first result: " + tracks.get(0).getName());
    }

    private static Response reply(String url, String text, int status) {
        return new Response(status, "", Collections.emptyMap(), text, url);
    }
}
