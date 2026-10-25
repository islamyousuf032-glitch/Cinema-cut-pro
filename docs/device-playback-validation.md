# Device playback validation

CI can verify Kotlin/JNI/native compilation and tests, but it cannot reproduce a device-specific stutter or crash. The current changes reduce playback-driven Compose recomposition, serialize FFmpeg JNI handle access, attempt Media3 recovery if FFmpeg engine setup fails, and retry failed FFmpeg hardware decoding with its included software decoder. These are source/build-verified changes; smooth playback and crash-free switching remain unconfirmed until tested on-device.

## Test the latest debug APK

Install the latest debug APK from the [GitHub pre-releases](https://github.com/islamyousuf032-glitch/Cinema-cut-pro/releases). It is debug-signed and intended for device testing, not Play Store distribution. Use the same imported clip for each engine so the comparison is meaningful.

1. Start with a short, ordinary MP4 that previously stuttered. Let it play for at least 30 seconds in Media3 (`AUTO` and, if useful, the direct Media3 option). Note visible stalls, audio sync, seek response, and whether the playhead advances smoothly.
2. Select LibVLC and repeat with the same source. Wait for the first frame; record whether the active engine remains LibVLC or falls back, plus any displayed error.
3. Select `FFMPEG_NATIVE` **while the clip is playing** (the reported crash trigger). Confirm the app stays open, the video resumes on the same source, and pause/resume, seek, and another engine switch work. Also try selecting FFmpeg while paused. In the settings menu compare the selected engine with `Active player`; a fallback is not proof that FFmpeg decoded the clip.
4. In the debug overlay, check `Engine`, `Source` (`ORIGINAL` or `PROXY`), `Proxy status`, `Source MIME`, `Playback failure`, and `Last error`. The diagnostics panel also shows `activeEngine`, `activeSource`, `previewState`, `lastPlaybackErrorCode`, `lastPlaybackFailureCategory`, `lastPlaybackMimeType`, and `lastError`. The `Video drops` metric is Media3-specific; assess VLC/FFmpeg stutter by visible playback rather than treating that counter as common to all engines. Local media paths and URIs are redacted.
5. If available, repeat with a short ProRes, ProRes RAW, or APV sample in FFmpeg. Record whether it opens, produces frames in time, and has usable color. FFmpeg includes those software decoders, but their inclusion does not guarantee a particular profile, bit depth, frame rate, or real-time performance on the phone. Compare any hardware-decoded format only where the device has a matching MediaCodec decoder.
6. If a generated proxy is selected and fails, confirm playback falls back to the original and records a separate proxy-playback warning. Proxy generation should remain `READY`; a renderer/decoder failure must not be reported as failed transcoding. In Project Media, regenerate a proxy if needed and verify its status survives leaving and reopening the project.
7. Tap `Pro Timeline`, confirm the landscape timeline opens, then close it and verify that the project, selection, playhead, and preview still work. Open Color and test scrub/play; the preview may temporarily use Media3 for live grading and restore the selected engine on exit.
8. Record the device/Android version, selected and active engine, container, video/audio codec, codec profile, bit depth/pixel format, resolution, frame rate, rotation, and color transfer/primaries if known. A “4K/10-bit” label alone does not identify a codec or HDR transfer function. For ordinary clips, use this information to compare the exact same asset across Media3, LibVLC, and FFmpeg.

## Optional debug-build Logcat

After reproducing an issue, collect the relevant tags:

```sh
adb logcat -c
# Reproduce the failure in the app, then:
adb logcat -d -s NativeVlcPreviewEngine:D FfmpegPreview:D VideoViewportSection:D PLAYER_MEDIA3:D
```

If the app closes, collect the crash buffer immediately:

```sh
adb logcat -b crash -d -v time
```

Share the `FATAL EXCEPTION` or native signal section plus the debug-panel values. Do not infer a codec or HDR failure from resolution/bit depth alone; an original file or short sample and its exact metadata may be needed to diagnose the cause.

## APK delivery

Every push to the active branch builds/tests the app and uploads a workflow artifact. To publish a GitHub pre-release as well, the push commit subject must include `[publish-test-release]`. The APK is debug-signed for device testing, not Play Store distribution, and the release includes a SHA-256 checksum.
