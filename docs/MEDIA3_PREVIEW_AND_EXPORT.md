# Media3 preview and bounded export

**Integration status:** the Android debug build, JVM tests, and native host tests passed in [GitHub Actions run 39122394286](https://github.com/islamyousuf032-glitch/Cinema-cut-pro/actions/runs/39122394286). Representative media playback/export and Logcat validation on target Android devices remain outstanding. This document describes implemented source scope and known limits; it is not a compatibility guarantee.

## Playback route

- `PreviewEngineType.AUTO` resolves to Media3 ExoPlayer. Media3 is the default for ordinary Android media; the timeline, project model, orientation, and existing editor/timeline UI remain in place.
- The old `FFMPEG_NATIVE` preference name is retained for saved-setting compatibility. Its implementation is the app's existing **LibVLC 3.5.1 Java API**, not a standalone native C++ FFmpeg decoder. `VLC_NATIVE` is also retained. These are explicit/manual fallback selections; an automatic Media3 failure does not silently load LibVLC.
- `PreviewMediaSource` carries the URI, MIME type, and proxy selection without depending on Compose. The timeline passes asset MIME (or proxy MIME) to Media3, which is especially useful when a Storage Access Framework URI has no useful filename extension.
- ExoPlayer receives SAF `content://` URIs through Android's content data source and the app already attempts to persist read permission at import. The importer also copies media into app-scoped storage when possible. This path still needs testing with providers that deny persistent grants, cloud-backed documents, and relinked assets.
- The foundation exposes load/source, play, pause, seek, positive playback speed, normalized volume, state, first-frame, error, and release controls. The timeline applies each active clip's volume. Player objects use the application context, enable audio-focus handling/noisy-route pause behavior, and are released with their Compose owner.

### Format support is device-dependent

Media3 delegates demuxing and decoding to its configured extractors and the Android platform's available codecs. A container or codec that works on one Android version/device may fail on another, and a MIME hint cannot add a missing decoder. Do not describe this as support for “all formats.” In particular, `.mcraw` is not supported and no MCRAW decoder was added.

For a real compatibility claim, record exact source details (container, video/audio codec and profile, bit depth, frame rate, HDR/rotation), Android version, device/model, and the Media3 error. The in-app debug panel reports engine/state, first frame, URI scheme/authority (not the private path), source MIME, Media3 error category/code name and cause, decoder initialization, dropped frames, and frame-processing metrics. Logcat or `adb logcat` should be captured alongside the sample; debug diagnostics are not a substitute for a device test.

## Export proof of concept and limits

The repository already contains a cancellable Media3 Transformer path. It is deliberately bounded and validates before rendering. Current validation allows a narrow sequential/cut-only composition: one enabled visible video track, regular video clips with compatible constant frame rates, contiguous timeline positions, valid source-frame trims, H.264 in MP4, 8-bit Rec.709 SDR, and compatible embedded mono/stereo AAC when present. The adapter uses platform encoder selection, reports progress, responds to cancellation, removes incomplete staging output, and copies a completed file to the chosen destination through the existing export workflow. Some basic clip adjustments are supported; unsupported adjustment stacks are rejected rather than silently dropped.

It is **not** a professional NLE renderer. It does not composite multiple video layers, add independent audio tracks/mixes, render clip transforms/keyframes, apply the complete color/LUT/HDR graph, watermark, or produce alpha. VFR inputs, unsupported rates/codecs, and other mismatches are rejected. Multiple accepted clips are rendered sequentially as cuts. There is no transition model/render path here: overlaps, crossfades/dissolves, and other timeline transitions are unsupported. Media3 Transformer alone does not provide the app's missing NLE transition semantics.

The export-oriented “Fix automatically” operation can shift clips to compact a sole visible video track. Review the resulting timeline before export; do not use it to imply that an intentional overlap/crossfade was rendered.

## Validation still required

The upload kit contained neither a sample video nor a Logcat capture, and this sandbox has no attached Android device. Consequently, the original playback failure has not been diagnosed, and playback/export have not been validated on target devices. Do not infer a device-specific cause from source inspection alone.

For validation, use user-provided/licensed fixtures and capture both the file and Logcat. At minimum, test:

1. A known-good H.264/AAC MP4 from local storage and the same file through a SAF `content://` URI.
2. A representative target-device camera clip (including its actual codec/profile, frame rate, rotation, bit depth, and audio details).
3. One media type that previously failed, without relabeling or transcoding it before the diagnostic run.
4. Timeline loading, first frame while paused, play/pause, frame seek, speed, per-clip volume, clip boundary handoff, background/noisy-audio behavior, and returning from a document provider.
5. A supported short trim export, output playback/metadata inspection, cancellation during encoding, and a project rejected for unsupported overlap/crossfade or track composition.

Record device/OS, sample checksums, source metadata, Media3 error code/cause, and export output metadata. This checklist is future validation work, not a report that those tests have passed.
