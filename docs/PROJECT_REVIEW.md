# Cinema Cut Pro — source review

**Review date:** 2026-10-10

**Scope:** static inspection of the Android project extracted from the repository's uploaded ZIP. This is a code review, not a device QA report.

## Executive summary

This is an **Android-first, native video-editing application**, not a web app. It has a substantial Compose/Kotlin editor and domain model, a separate landscape timeline workspace, media import/preview scaffolding, color and transform tools, and several export/native paths. The implementation is mixed: some editing and image-processing math is real, while several preview/export/native functions are explicitly placeholders. The source alone does not establish that the app can reliably render and export an edited, multi-layer project on a real phone.

A crucial product distinction: the current Gemini integration is for **textual color-grading advice**. The repository does not implement prompt-to-video generation. If “advanced video creation” means generating footage with AI, that requires a separate model/provider and service design.

## Architecture map

### App and state flow

- `app/src/main/java/com/example/MainActivity.kt` hosts the Compose app; `ChromaProApp.kt` wires the main editor, view models, preview controller, and export UI.
- `timeline/core/` contains `TimelineProject`, tracks/clips/settings, edit commands, validation, undo/redo, autosave, project versions, and file storage.
- `timeline/ui/TimelineViewModel.kt` exposes project/editor state as `StateFlow` and applies timeline commands. The advanced workspace is a separate `AdvancedTimelineActivity`; `ActiveProjectManager` transfers a project snapshot and playhead state between activities.
- `timeline/engine/preview/` owns playback selection/synchronization, frame lookup, still-frame preview, thumbnails, and transform/color preview adapters.
- `timeline/media/` contains URI import/analysis, metadata, codec checks, relinking, and proxy-work scaffolding.
- `timeline/export/` contains validation, render planning, export backends, MediaCodec decode/encode/mux helpers, progress, cancellation, and a foreground service.
- `model/adjustments/` and `model/colorgrade/` contain parameter stacks, keyframes, LUT and color math, CPU reference paths, and shader source.
- `ui/` contains the Compose editor, timeline workspaces, color/transform controls, scopes, and export settings.
- `app/src/main/cpp/` contains C++17 libraries exposed through JNI. These include timeline/transform math and export/color/compositing APIs; not every JNI entry has a real implementation.

### Technology and packaging

- Android Gradle project: root `settings.gradle.kts`, one application module (`:app`).
- Kotlin 2.2.10, Jetpack Compose, Android Gradle Plugin 9.1.1, Gradle wrapper 9.3.1; the app compiles Java source/target 11.
- `minSdk 24`, `targetSdk 36`, compile SDK API 36 minor level 1.
- CMake 3.22.1, C++17, currently filtered to `arm64-v8a`.
- The Kotlin namespace is `com.example`; the application ID is the generated-looking `com.aistudio.chromapro.xzvqpw`. Decide on a stable package ID before a public release. JNI function names also include `com_example`, so package migration must update native symbols and tests together.
- The visible Android app label is `Cinema Cut Pro`; several internal UI/AI strings still say “ChromaPro”.

## Capability assessment from the checked-in source

| Area | What is present | What is not established / needs work |
|---|---|---|
| Project and timeline | Track/clip/project models; frame-rate settings; editing commands; track controls; history; autosave/version storage; basic and advanced timeline UI. | There are few timeline-core tests. Edge cases such as cross-track ripple, locked tracks, source handles, and undo/redo persistence need automated coverage. |
| Media import and preview | Android media/URI abstractions, analyzers, Media3 and other preview adapters, still-frame fallback, and proxy-related classes. | Preview engine options are not equally complete or device-tested. Persisted URI permissions, rotation, variable-frame-rate clips, and codec fallback need validation on target devices. |
| Transform and motion | Transform data/keyframes, interpolation and motion-path code, native math, and editor controls. | Some JNI transform calls are hard-coded placeholders (`nativeComputeBoundingBox`, `nativeHitTestHandles`, and related functions in `native_transform_jni.cpp`). A UI control or declared engine does not guarantee the result is applied to exported pixels. |
| Color and LUT | Adjustment/grade stacks, CPU frame processors, LUT parsing/interpolation code, color UI, and GLSL source. | `OpenGLAdjustmentRenderer` currently returns its input texture unchanged; `ColorSpaceTransformEngine` documents simplified placeholder transforms; native layer-stack entry points are stubs. CPU/GPU/preview/export parity is not demonstrated. HDR, log, and 10-bit labels should be treated as aspirations until validated with known test footage and scopes. |
| Export | Queue/progress/service lifecycle plus a real Media3 Transformer adapter for one visible video clip, source-frame trim, output resize, H.264/MP4, and compatible embedded AAC. It rejects unsupported project edits and encoder formats instead of falling through to placeholder exporters. | It is **not production-ready or device-verified**. Android/Kotlin compilation and an actual MP4 export have not been run in this sandbox. The Media3 adapter does not composite multiple layers, apply transforms/grades/LUTs/watermarks, mix separate audio, or export HDR. The separate `VideoExportPipeline.kt` still selects only the top enabled video layer. Other backend scaffolding remains non-production. |
| Audio | Audio-track models, stream helpers, a PCM mixer, and mux/encode scaffolding. | There is no validated end-to-end multitrack audio mix/export report. Verify timing, channel mapping, clipping/limiting, mute/solo, and sync against test media. |
| AI | `MainViewModel` calls `GeminiRepository` for a color-advice chat; Retrofit targets the Google Generative Language API. | This is text advice, not video generation. `BuildConfig.GEMINI_API_KEY` is embedded in the client binary when configured; that is not safe for a public app. Move production calls behind a backend. |
| Tests | A Robolectric/Compose smoke-test scaffold and a small color-grading unit-test file. | There are no meaningful end-to-end import, timeline-edit, render/export, or device-compatibility suites. The imported archive's `build.log` was generated elsewhere and was not treated as proof of the current checkout building. |

### Source evidence for the export caveat

- `timeline/export/backend/Media3TransformerBackend.kt` now builds a Media3 `Transformer`, configures H.264/AAC encoders, applies source clipping and output presentation sizing, reports progress, and cleans up on success, failure, or cancellation. Its validator deliberately rejects unsupported edits.
- `timeline/export/pipeline/VideoExportPipeline.kt` remains a separate, incomplete path: it selects only the top enabled video layer and does not demonstrate the full compositing/color graph.
- `timeline/export/backend/NativeSoftwareExportBackend.kt` still has a simulated completion path and is not selected as a production fallback.
- `app/src/main/cpp/` now contains real host-testable frame/compositing, color/LUT, transform, PCM, and checked JNI buffer routines. The custom C++ frame pipeline is not yet connected to the Media3 MP4 path; host tests do not validate JNI or Android integration.

These are implementation and validation gaps, not just documentation gaps. Do not market export as a fully working multi-track renderer until the Android app has built and representative files have been inspected and tested on devices.

## Repository cleanup performed

- Extracted the Android project into the repository root while excluding Gradle cache and CMake/NDK build outputs (`.gradle/`, `app/.cxx/`, and generated build directories).
- Moved the duplicate `MediaLibrarySheet.kt` copy out of the orphaned `app/applet` tree after confirming it was byte-for-byte identical to the active source file.
- Consolidated imported audit notes under `docs/archive/imported/`; preserved the original ZIP in `archives/`.
- Removed archive-only build noise (`build.log`, the unused Node `package.json`, an unused network-check Gradle script, and an empty AI Studio placeholder assets tree).
- Added Android/Gradle/secrets ignores, made `gradlew` executable, restored the Gradle 9.3.1 wrapper JAR from its upstream Git blob after detecting that the uploaded copy was corrupt, and removed the need for a checked-in/local `debug.keystore` for debug builds.

## Validation limits

The sandbox has no `java`, Android SDK, or Android NDK, so Gradle/Kotlin compilation, JNI/Android native compilation, Android unit tests, and device playback/export could not be run. The Python host-test runner did compile the C++ kernels with the available host compiler and passed deterministic compositor, I420, color/LUT, transform, and PCM tests. That does not establish JNI linkage or a valid MP4 on a phone. A compatible Android Studio environment must build the project and export/inspect representative test media before the import-edit-export path is considered verified.