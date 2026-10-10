# Recommended roadmap

The goal is a dependable advanced editor, not just a large set of controls. Build one correct media path at a time, and keep the preview and exported pixels consistent.

## Phase 0 — Establish a trustworthy baseline

1. Open the repository root in Android Studio and build `:app:assembleDebug` with the documented SDK/NDK toolchain.
2. Run `:app:testDebugUnitTest`; record the failing/passing baseline rather than relying on imported status notes.
3. Add CI for Kotlin tests, lint, and native compilation. Keep large media fixtures out of Git; generate tiny deterministic fixtures during tests where possible.
4. Decide on the final application ID, visible branding, and distribution target before publishing. Do not rename the JNI namespace casually.

**Exit criteria:** a clean checkout builds reproducibly, tests run, and a debug app opens on at least one arm64 device.

## Phase 1 — Prove one end-to-end edit and export

1. Import a short, known H.264 MP4 through Android's document picker and retain the required URI permission.
2. Create a project, place one video clip, preview it, split/trim it on exact frame boundaries, save, close, and reopen.
3. Build and validate the bounded Media3 H.264/MP4 adapter on an Android device. Export that one-layer edit; inspect duration, frame rate, rotation, audio sync, playback/share, and cancellation/failure cleanup. The adapter code exists, but this device validation remains outstanding.
4. Add a golden test fixture and inspect output frames/audio. Fail clearly for unsupported codecs and formats rather than producing a plausible but incorrect file.
5. Make export validation match the actual encoder/muxer. For example, do not advertise MKV/HDR/10-bit/AV1 merely because fields are present in a capability object.

**Exit criteria:** automated coverage verifies source in/out, timeline duration, timestamps, audio/video sync, and output metadata for the supported one-clip format matrix.

## Phase 2 — Use one render graph for preview and export

1. Define a frame/render-plan contract: rational project timebase; deterministic active clips per track; source-time mapping; ordering; enabled/hidden/muted state; alpha; transforms; effects; color metadata; and audio automation.
2. Implement a real multi-layer compositor and explicit pixel formats/color metadata. Convert decoder output to the chosen working format deliberately; do not pass arbitrary decoder YUV buffers straight to an encoder and assume the layouts match.
3. Apply transforms, crop, opacity, blend modes, adjustment/color-grade stacks, and LUTs using the same ordered effect graph in preview and final export.
4. Add synthetic pixel tests (two colors, alpha edges, crop/rotation, known LUT) and compare CPU/GPU paths within an agreed tolerance.
5. Remove or hide backend options that do not execute real rendering. Do not let stub backends emit successful export states.

**Exit criteria:** two or more overlapping video layers with transforms and a grade render identically in paused preview and exported output within defined tolerances.

## Phase 3 — Make timeline editing dependable

1. Add unit/property tests for move, trim, split, ripple, roll, slip, slide, snapping, and undo/redo, including gaps, locked clips/tracks, linked audio, media handles, and frame zero.
2. Store all edit times as integer frames plus explicit rational frame rates; test 23.976, 29.97, 59.94, and variable-frame-rate source mapping.
3. Ensure every user-visible action is a command with atomic validation, a meaningful error, undo/redo, autosave, and recovery behavior.
4. Exercise transitions between the portrait editor and landscape timeline, process/activity recreation, media-resource release, and project reload.
5. Add the missing professional timeline affordances only after the underlying operation is covered: track add/rename/reorder, linked clips, markers, ripple modes, and keyboard/tool shortcuts.

**Exit criteria:** edit operations preserve invariants, survive save/reopen, and have automated tests with no silent partial updates.

## Phase 4 — Build a color pipeline that can be trusted

1. Pick and document the working color space and transfer function; keep display transforms separate from grading operations.
2. Implement real camera-log input transforms and explicit Rec.709 / Rec.2020 / HLG / PQ transforms. Respect transfer function, primaries, matrix coefficients, range, bit depth, and metadata.
3. Finish LUT parsing/application (including domain/range and interpolation), tone mapping, and an actually connected GPU renderer; keep a correct CPU reference implementation.
4. Add color bars, gray ramps, known LUTs, waveform, histogram, and vectorscope tests to expose clipping, channel swaps, and preview/export mismatch.
5. Provide a compatibility fallback for devices that cannot compile or run the chosen GPU shaders.

**Exit criteria:** documented reference inputs produce expected pixel values on CPU, GPU, preview, and export paths on the target device matrix.

## Phase 5 — Audio, performance, and device robustness

- Add deterministic multi-track audio mixing, volume/keyframe automation, channel layout conversion, clipping protection, and A/V sync tests.
- Bound decoder/bitmap/texture caches; close codecs, extractors, surfaces, and native handles on success, error, cancellation, and lifecycle changes.
- Test proxy creation, relinking, missing media, low-storage conditions, background export notifications, process death, and export resume/cancel.
- Profile on low/mid/high-end arm64 devices; measure dropped preview frames, export speed, peak memory, thermal behavior, and battery use.

## Phase 6 — AI and release readiness

- If AI is a product feature, scope it deliberately: color advice, edit suggestions, captioning, or actual generative video are different features with different costs and consent requirements.
- Keep API credentials and provider calls behind a backend for a public release. Add rate limits, error handling, privacy disclosures, and opt-in behavior before uploading user media.
- Add accessibility, localization, privacy/data-retention documentation, crash reporting policy, license review, release signing, minification checks, and a tested device/codec support matrix.

## Suggested order of work

The highest-risk unknown is **correct full-project export**. Start with Phase 0 and the one-clip export slice in Phase 1, then build the shared render graph in Phase 2. More UI, more codec labels, or more “pro” buttons will not make the app advanced if the exported image and sound do not match the edit.