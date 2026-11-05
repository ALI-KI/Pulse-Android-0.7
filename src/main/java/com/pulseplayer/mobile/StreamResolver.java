package com.pulseplayer.mobile;

import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class StreamResolver {
    static final String AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36";
    static {
        NewPipe.init(new Downloader() {
            @Override public Response execute(Request request) throws IOException, ReCaptchaException {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                URL url = new URL(request.url());
                if (!"https".equals(url.getProtocol())) throw new IOException("Expected HTTPS");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(10000);
                    connection.setRequestMethod(request.httpMethod());
                    connection.setRequestProperty("User-Agent", AGENT);
                    if ("api-mobi.soundcloud.com".equals(url.getHost()))
                        connection.setRequestProperty("Origin", "https://m.soundcloud.com");
                    request.headers().forEach((key, values) -> {
                        if (!"Accept-Encoding".equalsIgnoreCase(key) && !values.isEmpty())
                            connection.setRequestProperty(key, String.join(", ", values));
                    });
                    byte[] data = request.dataToSend();
                    if (data != null) {
                        connection.setDoOutput(true);
                        try (OutputStream body = connection.getOutputStream()) { body.write(data); }
                    }
                    int status = connection.getResponseCode();
                    if (status == 429) throw new ReCaptchaException("Too many requests", request.url());
                    InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    String body = "";
                    if (input != null) {
                        try (InputStream stream = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                            byte[] chunk = new byte[8192];
                            for (int size; (size = stream.read(chunk)) != -1;) {
                                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                                if (bytes.size() + size > 16 * 1024 * 1024) throw new IOException("Response too large");
                                bytes.write(chunk, 0, size);
                            }
                            body = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                        }
                    }
                    return new Response(status, connection.getResponseMessage(), connection.getHeaderFields(),
                        body, connection.getURL().toString());
                } finally { connection.disconnect(); }
            }
        });
    }

    static final class Result {
        final String title;
        final List<String> urls;
        final String artwork;
        Result(String title, String artwork, List<String> urls) {
            this.title = title; this.artwork = artwork; this.urls = urls;
        }
    }

    static Result resolve(String url) throws Exception {
        if ("SoundCloud".equals(Track.provider(url))) {
            SoundCloudAudio track = SoundCloudAudio.resolve(url, NewPipe.getDownloader()::get);
            return new Result(track.title, track.artwork, candidates(track.streams, Collections.emptyList()));
        }
        StreamInfo info = StreamInfo.getInfo(url);
        List<String> urls = candidates(info.getAudioStreams(), info.getVideoStreams());
        if (urls.isEmpty()) throw new IOException("No playable audio");
        return new Result(info.getName(), artwork(info.getThumbnails()), urls);
    }

    static final class DownloadSource {
        final String url;
        final String extension;
        DownloadSource(String url, String extension) { this.url = url; this.extension = extension; }
    }

    static DownloadSource downloadSource(String url) throws Exception {
        List<AudioStream> streams = "SoundCloud".equals(Track.provider(url))
            ? SoundCloudAudio.resolve(url, NewPipe.getDownloader()::get).streams : StreamInfo.getInfo(url).getAudioStreams();
        DownloadSource source = downloadableAudio(streams);
        if (source == null) throw new IOException("No standalone audio file available");
        return source;
    }

    static DownloadSource downloadableAudio(List<AudioStream> streams) {
        for (AudioStream stream : orderedAudio(streams)) {
            if (!usable(stream) || stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP
                || stream.getFormat() == null) continue;
            String suffix = stream.getFormat().getSuffix();
            if (Arrays.asList("m4a", "mp3", "aac", "webm", "opus", "ogg").contains(suffix))
                return new DownloadSource(stream.getContent(), "." + suffix);
        }
        return null;
    }

    private static List<AudioStream> orderedAudio(List<AudioStream> audio) {
        List<AudioStream> ordered = new ArrayList<>(audio);
        ordered.sort(Comparator.comparing((AudioStream stream) -> stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP)
            .thenComparing(stream -> stream.getFormat() != MediaFormat.M4A && stream.getFormat() != MediaFormat.MP3)
            .thenComparing(Comparator.comparingInt(AudioStream::getAverageBitrate).reversed()));
        return ordered;
    }

    static List<String> candidates(List<AudioStream> audio, List<VideoStream> muxed) {
        Set<String> urls = new LinkedHashSet<>();
        for (AudioStream stream : orderedAudio(audio)) {
            if (usable(stream)) urls.add(stream.getContent());
            if (urls.size() == 3) break;
        }
        for (VideoStream stream : muxed) {
            if (!stream.isVideoOnly() && usable(stream)) {
                urls.add(stream.getContent());
                break;
            }
        }
        return new ArrayList<>(urls);
    }

    private static boolean usable(Stream stream) {
        return stream.isUrl() && stream.getContent() != null && stream.getContent().startsWith("https://")
            && (stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                || stream.getDeliveryMethod() == DeliveryMethod.HLS);
    }

    static String artwork(List<Image> images) {
        for (Image image : images) {
            if (image.getWidth() >= 240 && image.getWidth() <= 800) return image.getUrl();
        }
        return images.isEmpty() ? "" : images.get(0).getUrl();
    }

    static List<StreamInfoItem> search(String provider, String query) throws Exception {
        if ("SoundCloud".equals(provider)) return SoundCloudAudio.search(query, NewPipe.getDownloader()::get);
        StreamingService service = ServiceList.YouTube;
        SearchInfo info = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query));
        List<StreamInfoItem> results = new ArrayList<>();
        for (InfoItem item : info.getRelatedItems()) {
            if (item instanceof StreamInfoItem) results.add((StreamInfoItem) item);
        }
        return results;
    }
}
