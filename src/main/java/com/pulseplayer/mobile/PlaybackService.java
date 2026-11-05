package com.pulseplayer.mobile;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlaybackService extends Service {
    static PlaybackService active;
    static String lastError = "";
    private static final int NOTIFICATION = 42;
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean ready;
    private boolean resolving;
    private boolean soundCloud;
    private String failureStage = "";
    private java.io.File soundCloudBuffer;
    private int request;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService resolver = Executors.newFixedThreadPool(2);
    private Future<?> resolution;
    private Iterator<String> sources = Collections.emptyIterator();
    private final List<Track> queue = new ArrayList<>();
    private int queueIndex = -1;
    private Runnable prepareTimeout;
    private float speed = 1f;
    private boolean resumeOnFocus;
    private boolean wantsPlayback;
    String title = "";
    String artwork = "";

    @Override public void onCreate() {
        super.onCreate();
        active = this;
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("playback", "Pulse playback", NotificationManager.IMPORTANCE_LOW));
        audio = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes()).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(this::onFocus).build();
        session = new MediaSession(this, "Pulse");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopSelf(); }
            @Override public void onSeekTo(long position) { seek((int) position); }
            @Override public void onSkipToNext() { skip(1); }
            @Override public void onSkipToPrevious() { skip(-1); }
        });
        session.setActive(true);
        IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, noisyFilter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, noisyFilter);
    }

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { pause(); }
    };

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if ("toggle".equals(action)) { toggle(); return START_NOT_STICKY; }
        if ("next".equals(action)) { skip(1); return START_NOT_STICKY; }
        if ("previous".equals(action)) { skip(-1); return START_NOT_STICKY; }
        if ("stop".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (!"play".equals(action)) return START_NOT_STICKY;
        queue.clear();
        String serialized = intent.getStringExtra("queue");
        String queueToken = intent.getStringExtra("queue_token");
        if (queueToken != null) {
            android.content.SharedPreferences handoff = getSharedPreferences("pulse_queue_handoff", MODE_PRIVATE);
            serialized = handoff.getString(queueToken, null);
            handoff.edit().remove(queueToken).apply();
        }
        if (serialized != null) {
            try {
                org.json.JSONArray entries = new org.json.JSONArray(serialized);
                for (int i = 0; i < entries.length(); i++) {
                    Track track = Track.read(entries.getJSONObject(i));
                    if (track.uri != null && !track.uri.isEmpty()) queue.add(track);
                }
            } catch (org.json.JSONException invalid) { queue.clear(); }
        }
        if (queue.isEmpty()) {
            String uri = intent.getStringExtra("uri");
            if (uri == null || uri.isEmpty()) return START_NOT_STICKY;
            Track fallback = new Track(uri, intent.getStringExtra("title"));
            fallback.artwork = intent.getStringExtra("artwork");
            queue.add(fallback);
        }
        playAt(Math.max(0, Math.min(intent.getIntExtra("index", 0), queue.size() - 1)));
        return START_NOT_STICKY;
    }

    boolean hasNext() { return queueIndex >= 0 && queueIndex + 1 < queue.size(); }
    boolean hasPrevious() { return queueIndex > 0; }
    int queueSize() { return queue.size(); }
    int queuePosition() { return queueIndex; }

    void skip(int direction) {
        if (direction != -1 && direction != 1) return;
        int next = queueIndex + direction;
        if (next < 0 || next >= queue.size()) return;
        playAt(next);
    }

    private void completed() {
        if (hasNext()) skip(1);
        else {
            wantsPlayback = false;
            audio.abandonAudioFocusRequest(focus);
            update();
        }
    }

    private void playAt(int index) {
        if (index < 0 || index >= queue.size()) return;
        queueIndex = index;
        Track track = queue.get(index);
        int token = ++request;
        if (resolution != null) resolution.cancel(true);
        releasePlayer();
        audio.abandonAudioFocusRequest(focus);
        title = track.title == null || track.title.isEmpty() ? "مقطع صوتي" : track.title;
        artwork = track.artwork == null ? "" : track.artwork;
        lastError = "";
        failureStage = "";
        wantsPlayback = true;
        String source = track.uri;
        if (track.downloadId != 0) {
            try {
                Uri offline = getSystemService(DownloadManager.class).getUriForDownloadedFile(track.downloadId);
                if (offline != null) source = offline.toString();
            } catch (RuntimeException ignored) { /* Streaming or original local URI is the fallback. */ }
        }
        String media = PlatformLinks.media(source);
        if (!Track.provider(source).isEmpty() && !media.isEmpty()) source = media;
        resolving = !Track.provider(source).isEmpty();
        soundCloud = resolving && "SoundCloud".equals(Track.provider(source));
        sources = Collections.emptyIterator();
        startForeground(NOTIFICATION, notification());
        final String uri = source;
        if (resolving) {
            update();
            resolution = resolver.submit(() -> {
                try {
                    StreamResolver.Result result = StreamResolver.resolve(uri);
                    main.post(() -> {
                        if (token != request) return;
                        resolving = false;
                        if (result.title != null && !result.title.isEmpty()) title = result.title;
                        if (result.artwork != null && !result.artwork.isEmpty()) artwork = result.artwork;
                        sources = result.urls.iterator();
                        tryNextSource();
                    });
                } catch (Exception error) {
                    android.util.Log.w("Pulse", "Stream resolver: " + error.getClass().getSimpleName());
                    main.post(() -> { if (token == request) { failureStage = "SC-RESOLVE"; fail(); } });
                }
            });
        } else prepareSource(uri, false);
    }

    private void tryNextSource() {
        releasePlayer();
        if (!sources.hasNext()) { fail(); return; }
        String source = sources.next();
        if (!soundCloud) { prepareSource(source, true); return; }
        int token = request;
        resolving = true;
        update();
        resolution = resolver.submit(() -> {
            try {
                java.io.File file = SoundCloudBuffer.prepare(source, getCacheDir());
                main.post(() -> {
                    if (token != request) {
                        if (file != null) file.delete();
                        return;
                    }
                    resolving = false;
                    soundCloudBuffer = file;
                    prepareSource(file == null ? source : Uri.fromFile(file).toString(), true);
                });
            } catch (Exception error) {
                android.util.Log.w("Pulse", "SoundCloud buffer: " + error.getClass().getSimpleName());
                main.post(() -> {
                    if (token == request) {
                        failureStage = error.getMessage() != null && error.getMessage().contains("limit")
                            ? "SC-SIZE" : "SC-BUFFER";
                        tryNextSource();
                    }
                });
            }
        });
    }

    private void prepareSource(String uri, boolean platform) {
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(attributes());
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            if (platform && soundCloudBuffer == null) next.setDataSource(this, Uri.parse(uri),
                Collections.singletonMap("User-Agent", StreamResolver.AGENT));
            else next.setDataSource(this, Uri.parse(uri));
            next.setOnPreparedListener(prepared -> {
                if (player != prepared) return;
                clearPrepareTimeout();
                ready = true;
                // No display or surface is attached, including when a muxed source is used.
                if (wantsPlayback) resume(); else update();
            });
            next.setOnCompletionListener(completed -> {
                if (player == completed) completed();
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) {
                    failureStage = "PLAYER-" + what + "-" + extra;
                    if (platform && !ready) tryNextSource(); else fail();
                }
                return true;
            });
            next.prepareAsync();
            if (platform) {
                prepareTimeout = () -> { if (player == next && !ready) tryNextSource(); };
                main.postDelayed(prepareTimeout, 12000);
            }
            update();
        } catch (Exception error) {
            if (platform) tryNextSource(); else fail();
        }
    }

    private void clearPrepareTimeout() {
        if (prepareTimeout != null) main.removeCallbacks(prepareTimeout);
        prepareTimeout = null;
    }

    private void fail() {
        lastError = title + (soundCloud ? " [" + failureStage + "]" : "");
        if (hasNext() && wantsPlayback) {
            // Do not retry the failed entry indefinitely; advance through the finite queue.
            String error = lastError;
            skip(1);
            lastError = error;
        } else stopSelf();
    }

    boolean playing() { return ready && player != null && player.isPlaying(); }
    boolean pending() { return wantsPlayback && (resolving || player != null && !ready); }
    int duration() { return ready && player != null ? Math.max(0, player.getDuration()) : 0; }
    int position() { return ready && player != null ? Math.max(0, player.getCurrentPosition()) : 0; }

    void seek(int time) {
        if (ready && player != null && duration() > 0) {
            player.seekTo(Math.max(0, Math.min(time, duration())));
            update();
        }
    }

    void toggle() { if (playing() || pending()) pause(); else resume(); }
    private void pause() {
        wantsPlayback = false;
        resumeOnFocus = false;
        if (playing()) player.pause();
        audio.abandonAudioFocusRequest(focus);
        update();
    }
    private void resume() {
        wantsPlayback = true;
        if (ready && player != null && audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            startAtSpeed();
        }
        update();
    }
    float speed() { return speed; }

    void setSpeed(float value) {
        speed = value;
        if (playing()) startAtSpeed();
        update();
    }

    private void startAtSpeed() {
        try {
            player.setPlaybackParams(new PlaybackParams().allowDefaults().setSpeed(speed).setPitch(1f));
            player.start();
        } catch (IllegalArgumentException unsupportedSpeed) {
            speed = 1f;
            try { player.start(); } catch (IllegalStateException invalidState) { fail(); }
        } catch (IllegalStateException invalidState) {
            fail();
        }
    }

    private void onFocus(int change) {
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (resumeOnFocus && ready && player != null) startAtSpeed();
            resumeOnFocus = false;
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (playing()) {
            resumeOnFocus = true;
            player.pause();
        }
        update();
    }

    private PendingIntent serviceAction(String action, int id) {
        return PendingIntent.getService(this, id, new Intent(this, PlaybackService.class).setAction(action),
            PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "playback")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title)
            .setContentText(queue.size() > 1 ? (queueIndex + 1) + " / " + queue.size() + " • Pulse" : "Pulse")
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,
                "السابق", serviceAction("previous", 4)).build())
            .addAction(new Notification.Action.Builder(playing() ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play, playing() ? "Pause" : "Play", serviceAction("toggle", 2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,
                "التالي", serviceAction("next", 5)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)).build();
    }
    private void update() {
        int state = playing() ? PlaybackState.STATE_PLAYING
            : pending() ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration()).build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SEEK_TO |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS |
            PlaybackState.ACTION_STOP).setState(state, position(), playing() ? speed : 0).build());
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
    }
    private void releasePlayer() {
        clearPrepareTimeout();
        ready = false;
        resumeOnFocus = false;
        if (player != null) { player.release(); player = null; }
        if (soundCloudBuffer != null) { soundCloudBuffer.delete(); soundCloudBuffer = null; }
    }
    @Override public void onDestroy() {
        ++request;
        resolver.shutdownNow();
        active = null;
        unregisterReceiver(noisy);
        releasePlayer();
        audio.abandonAudioFocusRequest(focus);
        session.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
