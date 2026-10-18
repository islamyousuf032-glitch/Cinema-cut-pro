# Device playback validation

Playback failures can depend on the device decoder, the selected source, and the media's audio/video streams. CI can verify the build and unit tests, but it cannot reproduce a device-specific black preview.

## Test the latest debug APK

Install the test APK linked from the GitHub Actions run or its pre-release. It is debug-signed and intended for device testing, not Play Store distribution.

1. Open the affected project and play the same clip that failed before.
2. In the debug panel, check `activeEngine`, `activeSource`, `previewState`, `lastPlaybackErrorCode`, `lastPlaybackFailureCategory`, `lastPlaybackMimeType`, and `lastError`. The panel now scrolls vertically, so scroll it to inspect all values.
3. If `activeSource=PROXY` fails, the editor should mark that proxy failed and retry the original media automatically. Confirm whether playback then starts and whether `activeSource` changes to `ORIGINAL`.
4. Select LibVLC and wait up to five seconds for the first-frame watchdog or Media3 fallback. The LibVLC viewport now uses a TextureView so it can participate in the Compose viewport's clipping and transforms; record whether a VLC frame appears, whether the engine changes, and any error shown.
5. For a useful comparison, test the same short clip in Media3 and LibVLC. Include the device model, Android version/API level, source codec/profile, resolution, frame rate, and whether the clip has audio.

## Optional debug-build Logcat

After reproducing the issue, collect only the relevant tags:

```sh
adb logcat -c
# Reproduce the failure in the app, then:
adb logcat -d -s NativeVlcPreviewEngine:D VideoViewportSection:D PLAYER_MEDIA3:D
```

The LibVLC debug messages report attachment, video-output, first-frame-watchdog, and playback-event state. They intentionally avoid logging media paths. A short log excerpt and the debug-panel values are more useful than decoder initialization time alone.
