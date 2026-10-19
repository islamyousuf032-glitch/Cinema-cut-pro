# Device playback validation

Playback failures can depend on the device decoder, the selected source, and the media's audio/video streams. CI can verify the build and unit tests, but it cannot reproduce a device-specific black preview. The LibVLC sizing change and failure-reporting improvements are source-based fixes; playback success and the exact cause of the reported 4K HEVC proxy failure remain unconfirmed until tested on the affected device.

## Test the latest debug APK

Install the test APK linked from the GitHub Actions run or its pre-release. It is debug-signed and intended for device testing, not Play Store distribution.

1. Open the affected project and play the same clip that failed before. Check the viewport size before opening Color, after switching tabs, and after an orientation change; the LibVLC view now receives the same fit-mode policy as Media3 and reapplies it when its video output is ready.
2. In the debug overlay, check `Engine`, `Source` (`ORIGINAL` or `PROXY`), `Proxy status`, `Source MIME`, `Playback failure`, and `Last error`. Local media paths and URIs are redacted. The separate diagnostics panel still shows `activeEngine`, `activeSource`, `previewState`, `lastPlaybackErrorCode`, `lastPlaybackFailureCategory`, `lastPlaybackMimeType`, and `lastError`; scroll it to inspect all values.
3. If `activeSource=PROXY` fails, the editor should mark only that proxy failed and retry the original media automatically. Confirm whether playback then starts and whether the source changes to `ORIGINAL`.
4. In Project Media, use `Retry Proxy` on the affected 4K HEVC clip. The viewport status is now resolved from the active clip's asset in the current project snapshot and shows `Proxy Ready`/`Proxy Failed` with the failure details, so compare the same clip in both places. If the retry fails, note the surfaced Media3 Transformer error name/code and details; the proxy job is always Media3 Transformer, regardless of the selected playback engine.
5. Select LibVLC and wait up to five seconds for the first-frame watchdog or Media3 fallback. The LibVLC viewport uses a TextureView and now reapplies the selected fit mode; record whether a VLC frame appears, whether it is correctly sized without visiting Color, whether the engine changes, and any error shown.
6. Open Color to verify live grading still updates. The screen may temporarily switch to Media3 when the selected engine cannot render live grades, then restore the previous engine when leaving Color; the large explanatory banner has been removed.
7. For a useful comparison, test the same short clip in Media3 and LibVLC. Include the device model, Android version/API level, source codec/profile, resolution, frame rate, and whether the clip has audio.

## Optional debug-build Logcat

After reproducing the issue, collect only the relevant tags:

```sh
adb logcat -c
# Reproduce the failure in the app, then:
adb logcat -d -s NativeVlcPreviewEngine:D VideoViewportSection:D PLAYER_MEDIA3:D
```

The LibVLC debug messages report attachment, video-output, first-frame-watchdog, and playback-event state. They intentionally avoid logging media paths. A short log excerpt and the debug-panel values are more useful than decoder initialization time alone.
