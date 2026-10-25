# FFmpeg Android integration status

## Source and license review

- FFmpeg `n8.1.3` is pinned to upstream commit `1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7`. Its default build is LGPL v2.1 or later; GPL/version-3 and nonfree options remain disabled. The app packages the LGPL notice and build metadata.
- `tools/build_ffmpeg_android.sh` pins NDK `27.2.12479018`, builds arm64-v8a/API 24 shared libraries, enables JNI, and checks the requested decoder/demuxer configuration. The Gradle task `:app:buildFfmpegAndroid` supplies headers/libraries to CMake and packages the FFmpeg libraries and notices.
- The configured software decoders include H.264, HEVC, AV1, VP8, VP9, ProRes, ProRes RAW, and APV. Android MediaCodec wrappers are included for H.264, HEVC, AV1, VP8, VP9, MPEG-2, and MPEG-4. The MOV/MP4 and Matroska/MKV demuxers are enabled, as are the APV parser and raw APV demuxer. This is build-time inclusion, not a promise that a device's hardware decoder supports every codec/profile.
- Build products stay under `app/build/ffmpeg-android`; the pinned source is retained between builds. The script protects its private build and install-prefix directories and avoids recursively deleting the generated prefix.
- The previously inspected EkinStrop prebuilt remains unselected: its advertised minimum API 28 conflicts with this app's minSdk 24, and its inspected repository did not provide a reproducible binary-build recipe.

## Playback and grading status

- `FFMPEG_NATIVE` remains a distinct selectable engine; ExoPlayer/Media3 and LibVLC are retained. FFmpeg demuxes the video source and attempts a matching Android MediaCodec decoder, then retries with an included FFmpeg software decoder when the hardware path fails. ExoPlayer remains responsible for source audio.
- Media3/FFmpeg can demux MP4/MOV and MKV containers when the contained stream is supported; ProRes, ProRes RAW, and APV decoder components are built into the FFmpeg path. Actual playback still depends on the file's exact codec, profile, bit depth, pixel format, frame rate, and decoder behavior. The FFmpeg preview converts frames to 8-bit RGBA, so this change does **not** promise a full-precision 10-bit/HDR preview or real-time ProRes/APV playback on every device.
- Native FFmpeg handle access and close are serialized through one gate. Engine construction/switch failures are caught and the controller attempts Media3 recovery. These lifecycle protections and decoder fallback are source/build-verified, not device-runtime proof.
- Standard clips can use the FFmpeg route explicitly. For live color grading, the editor can temporarily route supported non-MCRAW sources through the Media3 GPU effect graph, then restore the requested engine. MCRAW remains on its C++ Bayer preview renderer; curves, HSL, LUTs, and advanced effects remain unsupported on that route.
- The shared Media3 preview/export effect path renders supported curves. Export validation rejects unsupported layers, animation, and effects instead of silently dropping them.

## Verification and device limits

- GitHub Actions run [40202985334](https://github.com/islamyousuf032-glitch/Cinema-cut-pro/actions/runs/40202985334) passed Kotlin and Android native compilation, JVM unit tests, native C++ host tests, FFmpeg script syntax, and the second `:app:buildFfmpegAndroid` output-validation invocation. The FFmpeg Android source/configuration build also completed as part of the Gradle work. This run did not publish an APK.
- The latest debug test APK and its SHA-256 checksum are published from the branch's [GitHub pre-releases](https://github.com/islamyousuf032-glitch/Cinema-cut-pro/releases). It is debug-signed for device testing, not Play Store distribution.
- No physical-device playback test has been run. Do not infer support for all codecs, HDR/10-bit media, MotionCam files, or the reported 4K/10-bit sample without examining the exact media metadata and device logs. Record codec/profile, pixel format/bit depth, frame rate, color transfer/primaries, resolution, and audio streams when diagnosing failures.
