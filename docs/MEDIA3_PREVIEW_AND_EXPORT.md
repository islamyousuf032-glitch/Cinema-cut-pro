# Playback, live grading, and bounded export

**Verification status:** the export, preview, and native-renderer code changes in commit `449e992` passed the Android debug build, JVM tests, and native host tests in workflow [39746028316](https://github.com/islamyousuf032-glitch/Cinema-cut-pro/actions/runs/39746028316). None of these changes has been validated on a target device, and the CI result is not a device-compatibility guarantee.

## Playback routes

- `PreviewEngineType.AUTO` resolves to Media3 ExoPlayer. The existing ExoPlayer and LibVLC routes remain available; timeline, project model, orientation, and editor/timeline UI are unchanged.
- The old `FFMPEG_NATIVE` preference name is retained for saved-setting compatibility. Its implementation is the app's existing **LibVLC 3.5.1 Java API**, not a standalone native C++ FFmpeg decoder. `VLC_NATIVE` is also retained. Standalone FFmpeg playback has **not** been integrated yet; it needs a reproducible Android build, minimum-SDK/ABI coverage, a real demux/decode/audio-sync/Surface path, and a licensing/provenance review. Do not label the legacy setting as FFmpeg support.
- `PreviewMediaSource` carries URI, MIME type, and proxy selection without depending on Compose. MIME hints help with SAF URIs that have no useful filename extension. SAF grant persistence, cloud-backed providers, and relinked assets still need device testing.
- Media3 reports engine/state, first-frame and failure information plus playback metrics. Debug-only logs avoid release logging. Diagnostics are not a substitute for inspecting the actual source and device log.

### MotionCam RAW (`.mcraw`)

The app has a native MotionCam decoder and RAW Bayer preview path. It is routed to the native preview automatically for MCRAW assets and supports live primary grading in the current RAW preview. Playback still decompresses full RAW frames before producing a reduced preview; the playing preview is capped at a 480-pixel long edge, and the Bayer renderer uses a shared sRGB lookup table and a single neighborhood walk per output pixel. This is an optimization, **not** a smooth-playback guarantee. A short device-captured sample and on-device frame-timing tests are still needed. The bounded H.264/MP4 export backend rejects `.mcraw` before rendering.

The grading screen identifies the RAW preview's supported controls (exposure, temperature/tint, primary Lift/Gamma/Gain/Offset, three-zone balance, brightness, contrast, and saturation). Curves, HSL, LUTs, and other advanced RAW grading are not reflected yet.

### Ordinary video and live grading

Media3 uses Android's installed extractors/decoders; support depends on device and OS. A MIME hint cannot add a missing decoder, and a 4K/10-bit label alone does not establish codec, profile, transfer function, or failure cause. The reported 4K 10-bit HLG playback problem remains undiagnosed without the exact stream metadata, sample, and logs.

When the grading screen is open for ordinary video, its current route keeps the Media3 GPU graph attached so slider changes can update shader parameters without rebuilding the graph for every change. A paused player seeks to the held position on first graph attachment and throttles subsequent redraw requests. Other requested engines are temporarily routed through Media3 for this preview; ExoPlayer and LibVLC are not removed. Device testing is still required because Media3 effect attachment/redraw behavior can vary by renderer/device. Curve controls are explicitly marked as not yet connected to the live preview or MP4 export. The MotionCam limitations are listed separately above.

## Bounded Media3 export

The only real video export backend currently registered is Media3 Transformer using the Android platform encoder. The export UI is being narrowed to the implemented path: H.264 in MP4, Rec.709 8-bit SDR, AAC, source-matched constant frame rate, and selected target bitrate/bitrate mode. Advertised controls for other codecs/containers, HDR, alpha, watermarking, proxy output, and audio normalization are removed or described as unavailable rather than treated as working features.

Before rendering, validation checks the selected H.264 encoder's dimensions, frame rate, target bitrate, and bitrate mode, and checks AAC sample rate, channel count, and bitrate when audio is included. It also rejects unsupported source formats/settings, including MCRAW, HDR/log/>8-bit input, VFR or unverified cadence, incompatible source rates, multiple active video tracks, audio mixes/separate audio clips, overlays, transforms, overlaps, transitions, unsupported grades, and invalid trims. Encoder capability queries are device-dependent; passing them is not proof that a complete export will succeed on every device.

The supported composition is one enabled visible video track with regular video clips, contiguous cut-only edits and compatible CFR sources; accepted clips are rendered sequentially. Embedded mono/stereo AAC is only passed through when the source clips have a compatible format. The path does not perform frame-rate conversion, sample-rate conversion, multi-track compositing, full color/LUT processing, or transition rendering. Unsupported edits must be rejected, not silently dropped.

“Fix automatically” can compact a sole visible video track. Review the timeline before export; this must not be presented as rendering an intentional gap, overlap, or transition.

## Validation still required

No target-device validation of the current working-tree changes has been performed. For the unresolved ordinary-video playback report, do not infer the cause: collect the original sample (or a short unmodified clip), exact container/video/audio codec and profile, bit depth, frame rate, HDR transfer/primaries, rotation, Android/device details, and Media3/LibVLC logs.

For playback and export, test SAF and app-local media, representative camera/MCRAW footage, paused first-frame and grade updates, play/pause/seek/speed/audio and clip-boundary handoff, short accepted trims, output metadata and playback, unsupported-setting rejection, cancellation, and incomplete-file cleanup. Record device/OS, sample checksums, source/output metadata, frame/drop metrics, and error codes. These are future tests, not claims that they have passed.
