package com.pulseplayer.mobile;

public final class PlatformLinksSmoke {
    public static void main(String[] args) {
        String id = "jNQXAC9IVRw";
        String canonical = "https://www.youtube.com/watch?v=" + id;
        expect(canonical, PlatformLinks.media("https://youtu.be/" + id + "?si=sample"));
        expect(canonical, PlatformLinks.media("https://m.youtube.com/watch?t=1&v=" + id));
        expect(canonical, PlatformLinks.media("https://www.youtube.com/shorts/" + id));
        expect("", PlatformLinks.media("https://m.youtube.com/results?search_query=Pulse"));
        expect("", PlatformLinks.media("https://youtube.com.example.org/watch?v=" + id));
        expect("", PlatformLinks.media("https://www.youtube.com/watch?v=<script>"));
        expect("https://soundcloud.com/forss/flickermood",
            PlatformLinks.media("https://m.soundcloud.com/forss/flickermood?utm_source=sample"));
        expect("https://soundcloud.com/forss/sets/sample",
            PlatformLinks.media("https://soundcloud.com/forss/sets/sample"));
        expect("", PlatformLinks.media("https://soundcloud.com/search/sounds?q=sample"));
        expect("", PlatformLinks.media("https://soundcloud.com/forss/tracks"));
        expect("https://on.soundcloud.com/example", PlatformLinks.media("https://on.soundcloud.com/example"));
        if (Track.downloadable(canonical)) throw new AssertionError("Platform video exposed as download");
        System.out.println("12 platform link checks passed");
    }
    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
