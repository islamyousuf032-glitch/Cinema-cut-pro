# Device playback validation

Playback failures can depend on the device decoder, the selected source, and the media's audio/video streams. CI can verify the build and unit tests, but it cannot reproduce a device-specific black preview or crash. The latest source changes separate proxy playback errors from generation status, make Color preview select the original, avoid enabling the GPU grade graph when no grade is active, retry effect-pipeline errors without the effect, and publish completed proxies from a temporary MP4. These are source-based fixes; playback success on the affected device and the exact media-specific cause remain unconfirmed until device testing.

## Test the latest debug APK

Install the test APK linked from the GitHub Actions run or its pre-release. It is debug-signed and intended for device testing, not Play Store distribution.

1. Open the affected project and play the same clip that failed before. Check the viewport size before opening Color, after switching tabs, and after an orientation change. In the portrait editor, the landscape LibVLC texture surface is normalized to the embedded canvas bounds instead of being sized from the phone's portrait dimensions.
2. In the debug overlay, check `Engine`, `Source` (`ORIGINAL` or `PROXY`), `Proxy status`, `Source MIME`, `Playback failure`, and `Last error`. Local media paths and URIs are redacted. The separate diagnostics panel still shows `activeEngine`, `activeSource`, `previewState`, `lastPlaybackErrorCode`, `lastPlaybackFailureCategory`, `lastPlaybackMimeType`, and `lastError`; scroll it to inspect all values.
3. If `activeSource=PROXY` fails, the editor should fall back to the original automatically and record a separate proxy-playback warning. Proxy generation should remain `READY`; a renderer or color-effect failure must not be reported as a failed transcode. Confirm whether playback starts and whether the source changes to `ORIGINAL`.
4. In Project Media, use `Regenerate Proxy` when a generated proxy has a playback warning, or `Generate Proxy` when none exists. Confirm the output reaches `READY`, switch away and back, then open Color. The viewport status is resolved from the active clip's asset; compare it with Project Media. If generation fails, note the surfaced Media3 Transformer error name/code and details; the proxy job is Media3 Transformer regardless of the selected playback engine.
5. Select LibVLC and wait up to five seconds for the first-frame watchdog or Media3 fallback. The LibVLC viewport uses a TextureView, preserves the selected fit mode, and corrects embedded landscape surface sizing inside the portrait editor. Record whether the video appears at the expected canvas size without visiting Color, whether the engine changes, and any error shown.
6. Tap `Pro Timeline`, confirm that the landscape timeline opens, then close it and verify that the same project, selection, playhead, and preview still work. The route reuses the editor's project and preview controller to avoid spinning up a second decoder/player for the mini viewport.
7. Open Color and verify that the preview uses the original rather than a proxy. Change one grade control, scrub, and play. The screen may temporarily switch to Media3 when the selected engine cannot render live grades, then restore the previous engine when leaving Color. With no active adjustment, the Media3 preview should not route frames through the GPU grade graph; if an effect-pipeline error does occur, it should retry once without that optional effect rather than blacking the source or poisoning its proxy status.
8. For a useful comparison, test the same short clip in Media3 and LibVLC. Include the device model, Android version/API level, container, codec/profile, bit depth if known, color transfer (SDR/HLG/PQ if known), resolution, frame rate, rotation, and whether the clip has audio. A 4K/10-bit label alone does not establish a codec or HDR transfer function.

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
