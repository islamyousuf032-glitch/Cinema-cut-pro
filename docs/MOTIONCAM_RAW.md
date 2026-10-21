# MotionCam Pro RAW (`.mcraw`)

## Current implementation

`.mcraw` documents are recognized by their original filename, MIME type, or the MotionCam container signature. They are imported as video assets, analyzed with the vendored MotionCam C++ decoder, and routed to a dedicated native preview engine. ExoPlayer and LibVLC remain available and unchanged for ordinary media; neither is used to parse a `.mcraw` recording.

The preview path decodes one 16-bit Bayer frame at a time and renders a bilinear demosaic (up to 960 px on the longest edge while paused/scrubbing; 480 px during playback). It uses the recording's CFA pattern, black/white levels, white balance, and forward color matrix when those fields are present. Frame timestamps are normalized relative to the first video frame. During playback it follows source time and skips late frames rather than making the clip run slower when decoding cannot keep up. The preview applies exposure, temperature/tint, Lift/Gamma/Gain/Offset wheels, and three-zone color balance; brightness, contrast, and saturation use the viewport's basic color matrix. Curves, HSL, LUTs, and other advanced grade tools are not yet reflected in the MCRAW viewport. This lightweight conversion is an editing preview, **not** a full MotionCam ISP or a color-accurate RAW development pipeline.

The decoder sources are pinned to upstream `mirsadm/motioncam-decoder` commit `b931ec0e2466c8217c49f13df925531b78f76e33`. License summaries and bundled dependency notices are in `app/src/main/cpp/third_party/motioncam_decoder/THIRD_PARTY_NOTICES.md`.

## Explicit limitations

- The app's native Android build is currently filtered to `arm64-v8a`.
- The decoder validates MCRAW container version 3; malformed or unsupported recordings remain in the media bin with an analysis error and are not added to the timeline.
- This path does not generate an MP4 proxy, use Media3 Transformer, or export `.mcraw` edits.
- Embedded PCM audio is detected and recorded in media metadata, but audio playback and export routing are not connected to this native preview yet.
- Gyroscope and accelerometer presence is reported, but stabilization is not applied.
- Real-time frame rate, memory use, camera color, rotation behavior, and document-provider permissions still need validation with a short MotionCam Pro recording on a physical device.

## Tests

`python3 tools/run_native_export_tests.py` builds and runs the host C++ export tests, the Bayer preview renderer test, and the upstream decoder, gyro, and accelerometer regression tests. The host suite does not compile JNI or prove Android device playback; the GitHub Actions Android build and on-device validation are required for those checks.
