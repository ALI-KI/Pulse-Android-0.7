package com.pulseplayer.mobile;

/** Local JSON playlist persistence and ordering regression check. */
public final class PlaylistSmoke {
    public static void main(String[] args) throws Exception {
        Playlist original = new Playlist("My driving mix 🎵");
        String local = "content://com.android.providers.media/documents/audio%3A12";
        String online = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
        String soundcloud = "https://soundcloud.com/forss/flickermood";
        original.uris.add(local);
        original.uris.add(online);
        original.uris.add(soundcloud);
        original.uris.add(online); // Legacy duplicate should be normalized on load.
        Playlist loaded = Playlist.read(original.write());
        if (!loaded.id.equals(original.id) || !loaded.name.equals(original.name))
            throw new AssertionError("Playlist ID/title was not preserved");
        if (loaded.uris.size() != 3 || !loaded.uris.get(0).equals(local)
            || !loaded.uris.get(1).equals(online) || !loaded.uris.get(2).equals(soundcloud))
            throw new AssertionError("Order, mixed providers or duplicate protection failed");
        Track downloaded = new Track(online, "Video soundtrack");
        downloaded.favorite = true;
        downloaded.downloadId = 2026L;
        Track roundtrip = Track.read(downloaded.write());
        if (!roundtrip.uri.equals(online) || !roundtrip.favorite || roundtrip.downloadId != 2026L)
            throw new AssertionError("Track metadata/download ID did not roundtrip");
        System.out.println("Playlist storage: ordered local + YouTube + SoundCloud, dedupe and download fields OK");
    }
}
