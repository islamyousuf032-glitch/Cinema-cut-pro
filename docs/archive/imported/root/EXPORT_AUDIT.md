# Export Audit Report

This report documents the current architecture components related to video export, prior to fuller implementation.

## 1. Project & File Definitions
- **ProjectSettings**: Defined in `app/src/main/java/com/example/timeline/core/TimelineTypes.kt`. Contains properties like `resolutionWidth`, `resolutionHeight`, `frameRate`, `colorSpace`, etc.
- **TimelineProject**: Defined in `app/src/main/java/com/example/timeline/core/TimelineProject.kt`. Holds tracks, media assets, and `ProjectSettings`.
- **TimelineClip**: Defined in `app/src/main/java/com/example/timeline/core/TimelineClip.kt`. Contains `sourceIn`, `sourceOut`, `timelineStart`, `transform`, `adjustments`, and `colorGrade`.
- **MediaAsset**: Defined in `app/src/main/java/com/example/timeline/media/MediaAsset.kt`.
- **Media URI Store**: Stored in `MediaAsset` properties `localOriginalUriString` and `originalUriString`.
- **Project Files**: Stored via `ProjectStorage` in `context.filesDir + "/timeline_projects"`.
- **Output Files**: Currently targeted locally at `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)` inside `ExportDialog.kt`.

## 2. Editor States
- **Transform State**: Exists via `ClipTransform` which holds scale, rotation, translation, crop, blend modes, anchor points.
- **Color Grading State**: Exists in `TimelineClip` via `.colorGrade` (`ColorGradeStack`) and `.adjustments` (`AdjustmentStack`), and holds active LUT application.
- **Audio Track State**: Track-level muting exists (`isMuted` on `TimelineTrack`).

## 3. Existing Export Implementation Status
- **Export Button**: Operational. Currently opens `ExportDialog` where users select resolution, frame rate, output codec, container, and bitrate.
- **Export Pipeline**: Exists in a functional but basic form via `ExportManager.kt` using Media3 Transformer.
- **Dependencies**: 
    - **MediaCodec / MediaMuxer**: Yes (standard Android SDK).
    - **Media3 Transformer**: Yes (`androidx.media3:media3-transformer` is in `build.gradle.kts`).
    - **FFmpeg / LibVLC API**: **Missing** from dependencies. No bindings currently exist in the project, though there's a reference to a theoretical FFmpeg integration.
    - **C++ / NDK**: Yes, `native_export_transform_engine.cpp` and `NativeExportTransformEngine.kt` exist but native operations are commented out in `ExportFrameCompositor.kt`.
    - **Background Tasks**: `WorkManager` exists and is used in the app (e.g., `ImportWorkManager`), but `ExportManager` currently uses Coroutine Jobs.

## 4. Output Storage Permissions
- **Android Permissions**: The `AndroidManifest.xml` has `READ_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES` but **NO** `WRITE_EXTERNAL_STORAGE` permission. On Android 10+ using `getExternalStoragePublicDirectory` combined with MediaStore API or simply `context.getExternalFilesDir()` handles saving files without the need for `WRITE_EXTERNAL_STORAGE`.

## 5. Feasibility Status & Risks
- **Available Backends Now**: `androidx.media3.transformer.Transformer` is fully capable *now* and does not require FFmpeg. Media3 Transformer can utilize MediaCodec for hardware-accelerated processing and handles trimming via `ClippingConfiguration`.
- **Backend Requiring Dependency**: FFmpeg would require full NDK implementations like FFmpegKit. Attempting to claim FFmpeg is active without the actual C binaries is strictly forbidden.
- **What Must Not Be Faked**: We must not simulate progress. The `ExportManager` currently hooks correctly to Media3's `Transformer.getProgress()`. We must actually generate real frame composites or MediaCodec output rather than producing "dummy" MP4s. We should use real `EditedMediaItemSequence`.
- **Export Risks**: Missing custom transform effects mapping inside Media3. Currently, `ExportManager.kt` partially maps scale/rotation to Media3 `ScaleAndRotateTransformation` but it does not map more advanced custom parameters (perspective crop, blend mode, LUTs) unless we implement a custom OpenGL GLSL `GlShaderProgram` effect to run C++ code, or we disable them pending further framework development.
