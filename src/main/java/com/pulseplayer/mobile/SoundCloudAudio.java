package com.pulseplayer.mobile;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

final class SoundCloudAudio {
    interface Fetch { Response get(String url) throws Exception; }
    private static final String API = "https://api-v2.soundcloud.com/";
    private static final Pattern CLIENT_ID =
        Pattern.compile("[\"']?client_id[\"']?\\s*[:=]\\s*[\"']([A-Za-z0-9]{32})[\"']");
    private static String cachedClientId;

    final String title;
    final String artwork;
    final List<AudioStream> streams;
    private SoundCloudAudio(JsonObject track, List<AudioStream> streams) {
        title = track.getString("title", "");
        artwork = track.getString("artwork_url", "");
        this.streams = streams;
    }

    static SoundCloudAudio resolve(String url, Fetch fetch) throws Exception {
        URI uri = URI.create(url);
        if ("on.soundcloud.com".equalsIgnoreCase(uri.getHost()) || "snd.sc".equalsIgnoreCase(uri.getHost()))
            url = fetch.get(url).latestUrl();
        String canonical = PlatformLinks.media(url);
        if (canonical.isEmpty() || !"soundcloud.com".equals(URI.create(canonical).getHost())
                || canonical.contains("/sets/")) throw new IOException("Expected a public SoundCloud track");

        // Mobile track pages expose both the exact track and the public web client ID.
        // This path does not depend on the desktop landing page or widget redirect.
        Exception mobileFailure;
        try {
            String mobile = "https://m.soundcloud.com" + URI.create(canonical).getRawPath();
            return readMobilePage(fetch.get(mobile).responseBody(), fetch);
        } catch (Exception error) {
            mobileFailure = error;
        }
        try {
            return resolveViaApi(canonical, fetch);
        } catch (Exception error) {
            error.addSuppressed(mobileFailure);
            throw error;
        }
    }

    static List<StreamInfoItem> search(String query, Fetch fetch) throws Exception {
        String encoded = PlatformLinks.encode(query);
        Response page = fetch.get("https://m.soundcloud.com/search/sounds?q=" + encoded);
        String client = mobileClientId(mobileState(page.responseBody()));
        String request = "search/tracks?q=" + encoded + "&limit=25&client_id=" + client;
        Response result = fetch.get("https://api-mobi.soundcloud.com/" + request);
        if (result.responseCode() < 200 || result.responseCode() >= 300)
            result = fetch.get(API + request);
        return searchResults(json(result));
    }

    static List<StreamInfoItem> searchResults(JsonObject data) throws IOException {
        if (data.getArray("collection") == null) throw new IOException("SoundCloud search results missing");
        List<StreamInfoItem> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object value : data.getArray("collection")) {
            if (!(value instanceof JsonObject)) continue;
            JsonObject track = (JsonObject) value;
            String url = PlatformLinks.media(track.getString("permalink_url", ""));
            String title = track.getString("title", "");
            if (!"SoundCloud".equals(Track.provider(url)) || url.contains("/sets/")
                    || title.isEmpty() || !seen.add(url)) continue;
            StreamInfoItem item = new StreamInfoItem(ServiceList.SoundCloud.getServiceId(), url, title, StreamType.AUDIO_STREAM);
            JsonObject user = track.getObject("user");
            item.setUploaderName(user == null ? "" : user.getString("username", ""));
            item.setDuration(track.getLong("duration", 0) / 1000);
            String image = track.getString("artwork_url", "");
            if (Track.https(image)) item.setThumbnails(Collections.singletonList(
                new Image(image, 100, 100, Image.ResolutionLevel.LOW)));
            results.add(item);
        }
        return results;
    }

    private static JsonObject mobileState(String html) throws Exception {
        Element state = Jsoup.parse(html).getElementById("__NEXT_DATA__");
        if (state == null) throw new IOException("SoundCloud mobile data missing");
        return JsonParser.object().from(state.data());
    }

    private static String mobileClientId(JsonObject page) throws IOException {
        String client = page.getObject("runtimeConfig").getString("clientId", "");
        if (!client.matches("[A-Za-z0-9]{32}")) throw new IOException("SoundCloud client ID missing");
        return client;
    }

    private static SoundCloudAudio resolveViaApi(String canonical, Fetch fetch) throws Exception {
        String client = clientId(fetch, false);
        Response response = resolveTrack(canonical, client, fetch);
        if (response.responseCode() == 401 || response.responseCode() == 403) {
            client = clientId(fetch, true);
            response = resolveTrack(canonical, client, fetch);
        }
        return readTrack(json(response), client, fetch);
    }

    static SoundCloudAudio readMobilePage(String html, Fetch fetch) throws Exception {
        JsonObject page = mobileState(html);
        String client = mobileClientId(page);
        JsonObject props = page.getObject("props").getObject("pageProps");
        String urn = props.getString("trackUrn", "");
        JsonObject tracks = props.getObject("initialStoreState").getObject("entities").getObject("tracks");
        JsonObject track = tracks.getObject(urn).getObject("data");
        if (urn.isEmpty() || track.isEmpty())
            throw new IOException("Invalid SoundCloud mobile track data");
        return readTrack(track, client, fetch);
    }

    private static Response resolveTrack(String canonical, String client, Fetch fetch) throws Exception {
        return fetch.get(API + "resolve?url=" + PlatformLinks.encode(canonical) + "&client_id=" + client);
    }

    private static synchronized String clientId(Fetch fetch, boolean refresh) throws Exception {
        if (!refresh && cachedClientId != null) return cachedClientId;
        if (!refresh) {
            try {
                cachedClientId = SoundcloudParsingHelper.clientId();
                return cachedClientId;
            } catch (Exception ignored) { /* Try SoundCloud's alternate public web assets. */ }
        }
        for (String page : Arrays.asList("https://m.soundcloud.com/discover", "https://w.soundcloud.com/player/",
                "https://soundcloud.com/discover")) {
            try {
                String html = fetch.get(page).responseBody();
                String id = findClientId(html);
                if (id != null) return cachedClientId = id;
                List<Element> scripts = new ArrayList<>(Jsoup.parse(html, page).select("script[src]"));
                Collections.reverse(scripts);
                for (Element script : scripts) {
                    String url = script.absUrl("src");
                    if (!Track.https(url) || !url.contains(".js")) continue;
                    String host = URI.create(url).getHost();
                    if (!host.endsWith(".sndcdn.com") && !host.endsWith(".soundcloud.com")) continue;
                    try {
                        id = findClientId(fetch.get(url).responseBody());
                        if (id != null) return cachedClientId = id;
                    } catch (IOException ignored) { /* Try the next first-party script. */ }
                }
            } catch (IOException ignored) { /* Try the next first-party page. */ }
        }
        throw new IOException("SoundCloud client bootstrap failed");
    }

    static String findClientId(String text) {
        Matcher match = CLIENT_ID.matcher(text);
        return match.find() ? match.group(1) : null;
    }

    static SoundCloudAudio readTrack(JsonObject track, String client, Fetch fetch) throws Exception {
        String policy = track.getString("policy", "");
        if (!"ALLOW".equals(policy) && !"MONETIZE".equals(policy))
            throw new IOException("SoundCloud track unavailable");
        JsonObject media = track.getObject("media");
        if (media == null || media.getArray("transcodings") == null)
            throw new IOException("SoundCloud returned no transcodings");

        List<AudioStream> streams = new ArrayList<>();
        String auth = track.getString("track_authorization", "");
        for (Object item : media.getArray("transcodings")) {
            if (!(item instanceof JsonObject)) continue;
            JsonObject candidate = (JsonObject) item;
            JsonObject format = candidate.getObject("format");
            if (format == null || candidate.getBoolean("snipped")) continue;
            String protocol = format.getString("protocol", "");
            if (!"hls".equals(protocol) && !"progressive".equals(protocol)) continue;
            String endpoint = candidate.getString("url", "");
            if (!Track.https(endpoint)) continue;
            String host = URI.create(endpoint).getHost();
            if (!"api-v2.soundcloud.com".equals(host) && !"api-mobi.soundcloud.com".equals(host)) continue;
            String mime = format.getString("mime_type", "").toLowerCase(Locale.ROOT);
            String preset = candidate.getString("preset", "").toLowerCase(Locale.ROOT);
            MediaFormat codec = mime.startsWith("audio/mpeg;") || mime.equals("audio/mpeg") || preset.contains("mp3") ? MediaFormat.MP3
                : mime.contains("opus") || preset.contains("opus") ? MediaFormat.OPUS
                : mime.contains("mp4") || mime.contains("aac") || preset.contains("aac") ? MediaFormat.M4A : null;
            if (codec == null) continue;
            try {
                String request = endpoint + (endpoint.contains("?") ? "&" : "?") + "client_id=" + client;
                if (!auth.isEmpty()) request += "&track_authorization=" + PlatformLinks.encode(auth);
                String source = json(fetch.get(request)).getString("url", "");
                if (!Track.https(source)) continue;
                streams.add(new AudioStream.Builder().setId(preset.isEmpty() ? protocol : preset)
                    .setContent(source, true).setMediaFormat(codec)
                    .setAverageBitrate(preset.contains("160") ? 160 : 96)
                    .setDeliveryMethod("hls".equals(protocol) ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP)
                    .build());
            } catch (Exception ignored) { /* Try other available clear formats. */ }
        }
        if (streams.isEmpty()) throw new IOException("SoundCloud returned no playable clear audio");
        return new SoundCloudAudio(track, streams);
    }

    private static JsonObject json(Response response) throws Exception {
        if (response.responseCode() < 200 || response.responseCode() >= 300)
            throw new IOException("SoundCloud HTTP " + response.responseCode());
        return JsonParser.object().from(response.responseBody());
    }
}
