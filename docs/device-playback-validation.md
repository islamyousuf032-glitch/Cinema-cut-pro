# Device playback validation

Playback failures can depend on the device decoder, the selected source, and the media's audio/video streams. CI can verify the build and unit tests, but it cannot reproduce a device-specific black preview or crash. The current fixes preserve the video container MIME, retain a media-file extension on imported local copies, normalize LibVLC's embedded surface to the real landscape viewport in the portrait editor, and keep Pro Timeline on the existing editor/player instance. They are source-based fixes; playback success, proxy transcoding on the affected device, and the exact prior crash cause remain unconfirmed until device testing.

## Test the latest debug APK

Install the test APK linked from the GitHub Actions run or its pre-release. It is debug-signed and intended for device testing, not Play Store distribution.

1. Open the affected project and play the same clip that failed before. Check the viewport size before opening Color, after switching tabs, and after an orientation change. In the portrait editor, the landscape LibVLC texture surface is normalized to the embedded canvas bounds instead of being sized from the phone's portrait dimensions.
2. In the debug overlay, check `Engine`, `Source` (`ORIGINAL` or `PROXY`), `Proxy status`, `Source MIME`, `Playback failure`, and `Last error`. Local media paths and URIs are redacted. The separate diagnostics panel still shows `activeEngine`, `activeSource`, `previewState`, `lastPlaybackErrorCode`, `lastPlaybackFailureCategory`, `lastPlaybackMimeType`, and `lastError`; scroll it to inspect all values.
3. If `activeSource=PROXY` fails, the editor should mark only that proxy failed and retry the original media automatically. Confirm whether playback then starts and whether the source changes to `ORIGINAL`.
4. In Project Media, use `Retry Proxy` on the affected 4K HEVC clip. The viewport status is now resolved from the active clip's asset in the current project snapshot and shows `Proxy Ready`/`Proxy Failed` with the failure details, so compare the same clip in both places. If the retry fails, note the surfaced Media3 Transformer error name/code and details; the proxy job is always Media3 Transformer, regardless of the selected playback engine.
5. Select LibVLC and wait up to five seconds for the first-frame watchdog or Media3 fallback. The LibVLC viewport uses a TextureView, preserves the selected fit mode, and corrects embedded landscape surface sizing inside the portrait editor. Record whether the video appears at the expected canvas size without visiting Color, whether the engine changes, and any error shown.
6. Tap `Pro Timeline`, confirm that the landscape timeline opens, then close it and verify that the same project, selection, playhead, and preview still work. The route reuses the editor's project and preview controller to avoid spinning up a second decoder/player for the mini viewport.
7. Open Color to verify live grading still updates. The screen may temporarily switch to Media3 when the selected engine cannot render live grades, then restore the previous engine when leaving Color; the large explanatory banner has been removed. On phone-sized layouts, `Nodes`, `Presets`, and `Show Before` use a compact toolbar, while copy, paste, and reset remain available in the More menu.
8. For a useful comparison, test the same short clip in Media3 and LibVLC. Include the device model, Android version/API level, source codec/profile, resolution, frame rate, and whether the clip has audio.

## Optional debug-build Logcat

After reproducing the issue, collect only the relevant tags:

```sh
adb logcat -c
# Reproduce the failure in the app, then:
adb logcat -d -s NativeVlcPreviewEngine:D VideoViewportSection:D PLAYER_MEDIA3:D
```

The LibVLC debug messages report attachment, video-output, first-frame-watchdog, and playback-event state. They intentionally avoid logging media paths. If Pro Timeline still closes the app, collect the crash buffer immediately after reproducing:

```sh
adb logcat -b crash -d -v time
```

Share the `FATAL EXCEPTION` or native signal section plus the debug-panel values. A short log excerpt is more useful than decoder initialization time alone.

## APK delivery

Every push to this branch builds and tests the debug APK and uploads a workflow artifact. To also publish a GitHub pre-release, the push commit subject must include `[publish-test-release]`; the APK is debug-signed for device testing, not Play Store distribution. The release includes a SHA-256 checksum.
