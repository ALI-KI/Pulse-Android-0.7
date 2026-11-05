package com.pulseplayer.mobile;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class SoundCloudLongTrackSmoke {
    public static void main(String[] args) throws Exception {
        String manifest = new String(Files.readAllBytes(Paths.get(args[0])), java.nio.charset.StandardCharsets.UTF_8);
        int count = SoundCloudBuffer.fragments(URI.create("https://example.com/playlist.m3u8"), manifest).size();
        if (count != 649) throw new AssertionError("Expected initialization and 648 media fragments");
        long expectedBytes = (long) (107.99755 * 60 * 96000 / 8);
        if (expectedBytes <= 64L * 1024 * 1024 || expectedBytes >= SoundCloudBuffer.LIMIT)
            throw new AssertionError("Long AAC track must fit the updated buffer limit");
        System.out.println("108-minute SoundCloud manifest accepted: 648 fragments and 74 MiB estimate");
    }
}
