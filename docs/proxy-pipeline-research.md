# Proxy pipeline investigation

**Status:** source-level fixes and automated tests; not yet verified on the reported Android device or media file.

## What the repository actually uses

- Preview playback is AndroidX Media3 ExoPlayer, with LibVLC 3.5.1 available as a playback fallback. The proxy encoder is AndroidX Media3 Transformer 1.11.1; proxy generation does not use LibVLC's native C++/FFmpeg API.
- Imported stream metadata records the Android-reported color-transfer value and marks HLG/ST 2084 as HDR. The current analyzer does not identify bit depth. “4K 10-bit” alone is therefore not enough to claim that a particular file is HDR, HEVC, RAW, or unsupported.
- Proxy generation requests H.264 in MP4. Media3 Composition defaults to keeping HDR; its pinned 1.11.1 source recommends OpenGL tone mapping when an app needs HDR-to-SDR conversion. The proxy path now asks for that conversion only when Android metadata says the source is HDR and the proxy is configured for SDR.

## Root causes found in source

1. A player error while a READY proxy was selected changed the persisted *generation* status to FAILED. This did not distinguish a malformed proxy from a renderer, frame-processing, or color-effect error. Proxy playback failure is now tracked separately, and the original is selected without erasing successful generation state.
2. The Color screen switches to Media3 but previously inherited smooth-preview proxy preference. Color grading now forces original-media selection for the duration of that screen. Frame-processing/shader failures are not written back as source/proxy failures; Media3 also retries once without the optional GPU effect for that source.
3. Transformer wrote directly to the final proxy path. It now writes a sibling `.pending-….mp4` and publishes it with a same-filesystem atomic rename only after Transformer completion and non-empty-file validation, so readers never see a partial output as the completed proxy.

## Desktop NLE research and adaptation

- [Kdenlive `ProxyTask`](https://github.com/KDE/kdenlive/blob/master/src/jobs/proxytask.cpp) checks the transcoder result and verifies a non-empty output before updating the clip to use the proxy; it removes a failed output and reports the job log. Its source is GPL-3.0-only / KDE-accepted GPL.
- [Shotcut `ProxyManager`](https://github.com/mltframework/shotcut/blob/master/src/proxymanager.cpp) uses a distinct `.pending.mp4` marker while a proxy job is active and has cleanup for stale pending files. Its source is GPL-3.0-or-later.
- This app adapts the lifecycle principles (separate pending work, validate completion, then publish) rather than copying either GPL implementation into the Android app. Their FFmpeg/MLT desktop backends are not drop-in Android solutions.

## Validation still needed on the target file/device

1. Record the file's container, video codec/profile, reported bit depth if available, transfer (SDR/HLG/PQ), resolution, frame rate, and rotation. Do not share private media unless comfortable doing so.
2. Generate a proxy and confirm it reaches READY; switch away and back to the clip; open Color; change one grade parameter; scrub and play; leave Color; test both Media3 and LibVLC preview.
3. If failure remains, provide the app's sanitized playback/proxy error text plus device model, Android version, selected preview engine, and the action that triggers it. The actual exported file or a short sample is needed before making codec-specific conclusions.

The app remains a Media3-based editor: these changes do not add a promise of support for every codec, a general FFmpeg backend, RAW/MCRAW decoding, or full NLE transition processing through Transformer.
