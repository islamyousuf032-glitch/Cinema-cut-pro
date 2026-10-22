# FFmpeg Android integration status

## Source and license review

- Candidate source: upstream `FFmpeg/FFmpeg`, release tag `n8.1.3`, commit `1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7` (tag resolved from the GitHub mirror on 2026-10-22).
- The release's `LICENSE.md` says the default build is LGPL v2.1 or later; GPL components are opt-in through `--enable-gpl`. A future Android build must keep GPL/version-3 and nonfree options disabled unless the product's license plan changes, retain the exact source and license notices, and provide the relinking materials required for the chosen dynamic-linking arrangement.
- FFmpeg's configure help exposes Android JNI and MediaCodec support. `tools/build_ffmpeg_android.sh` now pins this commit and describes an arm64-v8a/API 24 dynamic-library build, with explicit checks that GPL, version-3, and nonfree configurations are not enabled. The script's shell syntax and minimum-API guard were checked, but no Android FFmpeg binary has been built or shipped from this checkout.
- Run `tools/build_ffmpeg_android.sh [output-directory]` to build into an isolated generated directory (default: `app/build/ffmpeg-android`). It retains the pinned source checkout and only cleans generated build/prefix directories carrying its marker.
- The script expects NDK `27.2.12479018`; an actual NDK configure/build remains unverified in this sandbox because it has no Android SDK/NDK.
- The previously inspected EkinStrop prebuilt remains unselected: its advertised minimum API 28 conflicts with this app's minSdk 24, and its inspected repository did not provide a reproducible binary build recipe.

## Integration requirements before enabling a player route

1. Reproducibly cross-build pinned upstream sources for arm64-v8a at the app's minimum supported API. Avoid opaque prebuilts and do not silently raise minSdk.
2. Package the FFmpeg shared libraries, JNI wrapper, exact source revision, notices, and any required relinking materials. Keep codec and external-library options within the approved LGPL-only configuration.
3. Implement an actual native playback path: SAF/content-URI file-descriptor demuxing, decoder lifecycle and seeking, frame scheduling/output, rotation and color metadata handling, and synchronized audio. Merely linking `libavcodec` or invoking an FFmpeg command is not playback support.
4. Keep ExoPlayer and LibVLC routes intact. Do not map the legacy `FFMPEG_NATIVE` setting to LibVLC and describe it as FFmpeg; it remains clearly labeled as a legacy LibVLC setting until a distinct engine is built and tested.
5. Validate representative SDR/HDR and codec samples on Android devices. In particular, do not claim the reported 4K 10-bit HLG MP4 works until its exact metadata/sample and device logs are examined.

**Status:** FFmpeg playback is not integrated. The `n8.1.3` source pin and licensing/configuration review are groundwork only; API 24 compilation, MediaCodec decode behavior, runtime playback, audio sync, and device tests remain outstanding.
