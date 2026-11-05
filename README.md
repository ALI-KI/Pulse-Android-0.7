# Pulse Android 0.7 Beta

**اقرأ [README-0.7-AR.md](README-0.7-AR.md) للتحديث الجديد وتعليمات البناء والتوقيع.**

# Original 0.6 reference notes

Native Arabic RTL player by Mahmoud Elgamsy. Android 8.0 or later. Install over 0.5 to keep the on-device library and favorites. The update uses the same application ID and signing certificate.

- Search YouTube or SoundCloud in Pulse and select Play or Save. Pasted clip links appear in the library. Titles and creators are filled from search or public metadata when available.
- A media playback foreground service owns the Android MediaPlayer, its partial wake lock, playback speed, media session and notification. The activity can close or the screen can lock independently of playback.
- The resolver tries up to three playable audio-only HTTPS candidates, preferring progressive audio. It can fall back to a combined stream with audio and video. No video surface is attached. A combined stream can use more mobile data.
- The discovery panel opens native search. The library and search show artwork when available. The bottom navigation separates the library, favorites, and offline downloads.
- Select Download audio from a track’s menu. Android DownloadManager stores the complete standalone audio file in app-specific storage and continues queued downloads independently. Completed copies take priority over streaming from every tab. HLS-only or muxed-only platform tracks do not produce an offline file in this build.
- SoundCloud playback reads public mobile track metadata first, expands short links, and supports unencrypted AAC HLS at 160 and 96 kbps. For clear fragmented-MP4 HLS VOD, Pulse prepares a temporary audio file before playback and discards it when playback is released. This can delay playback and is separate from the offline library. Restricted content is skipped.

## Validation
- SoundCloud mobile search returned 25 tracks for البقرة; parser and search fallback verified using the live responses.

- Android APK built for minSdk 26 and targetSdk 36. Signature and upgrade certificate checked.
- JVM smoke checks cover link normalization, audio-first candidate ranking, fallback to a combined stream, rejection of video-only streams, offline file selection, and rejection of playlists as offline audio files.
- A live SoundCloud track returned both AAC variants, and the resolver parsed both. Android audio output for SoundCloud still needs physical-device confirmation. Results depend on stream availability.

## Build on Linux

Requires Java, Eclipse ECJ 3.39.0, Android SDK API 36 platform and build-tools 36, Bash, zip and unzip. Supply a private test keystore with alias `pulse-beta`. Set `PULSE_ANDROID_JAR`, `PULSE_ANDROID_TOOLS`, `PULSE_ECJ_JAR`, `PULSE_SIGNING_STORE`, and `PULSE_SIGNING_PASSWORD`, then run `./build.sh`. Reuse the signing key for upgrades. No keys are included. The updated output is `build/Pulse-Android-0.7-beta.apk`.

`StreamResolver` wraps NewPipeExtractor 0.26.5. `PlatformActivity` renders native search results. `PlaybackService` prepares media candidates and controls playback. Signed source URLs are resolved when a track is played.

## Third-party source

The combined Android build includes GPL-3.0-or-later NewPipeExtractor. Its license and corresponding upstream source are in `third-party/`. The Pulse source retains its separate license notice. Dependency JARs are pinned by `third-party/dependencies.sha256`.

- [NewPipeExtractor 0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5)
- [Nanojson](https://github.com/TeamNewPipe/nanojson/tree/e9d656ddb49a412a5a0a5d5ef20ca7ef09549996)
- [jsoup 1.22.2](https://github.com/jhy/jsoup/tree/jsoup-1.22.2)
- [JSR-305 3.0.2 sources](https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2-sources.jar)
- [Protocol Buffers Java Lite 4.35.1 sources](https://repo.maven.apache.org/maven2/com/google/protobuf/protobuf-javalite/4.35.1/protobuf-javalite-4.35.1-sources.jar)
- [Rhino 1.8.1 sources](https://repo.maven.apache.org/maven2/org/mozilla/rhino/1.8.1/rhino-1.8.1-sources.jar)

## Pending SoundCloud long-track fix

The exact Maheer Al-Muaiqly Al-Baqarah result reported on 2026-11-04 is about 108 minutes, with 648 media fragments at AAC 96 kbps. Its estimated audio size is 74 MiB, beyond the previous 64 MiB temporary-buffer ceiling. The source raises the temporary ceiling to 512 MiB subject to available storage and reports the failing playback stage. A JVM regression check accepts the real 648-fragment manifest and its expected size.

This source change has not been built into an updated APK. The previous private signing key and Android build toolchain were transient and are no longer present in this environment. Android device playback remains unverified. This implementation buffers the full track before playback, which can cause a long startup wait.
