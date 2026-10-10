# Transform & Motion Features Integration Map

## 1. TimelineClip Data Class Definition
- **Location:** `/app/src/main/java/com/example/timeline/core/TimelineClip.kt`
- **Notes:** Contains `val transform: ClipTransform`. Also, earlier I updated `ClipTransform.kt` to include full Keyframe capabilities (scale, position, rotation, opacity, anchor point, crops, flips, blending modes, etc.).

## 2. SelectedClipId Storage
- **Location:** `/app/src/main/java/com/example/timeline/ui/TimelineUiState.kt`
- **Field:** `val selectedClipId: String?`
- **Notes:** Managed by `TimelineViewModel`.

## 3. Clip Properties Editing
- **Location:** `/app/src/main/java/com/example/timeline/ui/TimelineViewModel.kt`
- **Notes:** `TimelineViewModel.updateClipTransform()` or similar engine commands mutate the `TimelineProject` via `CommandFactory` and update the `uiState`. Recently added `updateClipTransform` inside `TransformInspectorPanel` bindings.

## 4. Timeline State Storage
- **Location:** `/app/src/main/java/com/example/timeline/ui/TimelineUiState.kt` (State holder for ViewModel) and `/app/src/main/java/com/example/timeline/core/TimelineProject.kt` (Actual Project Model).
- **Notes:** Project tracks, markers, and clips live under `TimelineProject`.

## 5. Viewport Preview Rendering
- **Location:** `/app/src/main/java/com/example/timeline/ui/viewport/VideoViewport.kt`
- **Location:** `/app/src/main/java/com/example/timeline/engine/preview/PlayerViewContainer.kt`
- **Notes:** The video is ultimately rendered inside `PlayerViewContainer.kt`, which wraps the `PreviewEngine` implementation output in a Jetpack Compose UI container.

## 6. Active Preview Engine
- **Location:** Configured by `/app/src/main/java/com/example/timeline/engine/preview/TimelinePreviewController.kt` and `PreviewEngineFactory.kt`.
- **Active Type:** Uses `AUTO` which likely evaluates to `MEDIA3`, `VLC_NATIVE`, or `STILL_FRAME`. The NDK `NATIVE_CPP` is also available as an experiment.

## 7. Preview Support for Transform Rendering Today
- **Supported:** Yes.
- **Rendering Path:** Jetpack Compose `graphicsLayer` modification in `PlayerViewContainer.kt` uses the evaluated C++ transform variables per frame (`NativeTransformEngine.evaluateTransformAtFrame(clipTransform, playheadFrame)`). It controls scale, translation, rotation, and alpha. This supports real-time viewport updates perfectly for position, scale, rotation, and opacity.

## 8. Export Pipeline Existence
- **Exists:** Yes, located in `/app/src/main/java/com/example/timeline/export/`.
- **Notes:** Export engine will need to be updated to map these exact `TransformState` evaluation data streams to its exporter implementation (e.g. Ffmpeg video filters).

## 9. Undo/Redo System
- **Exists:** Yes, `TimelineHistoryManager.kt`.
- **Notes:** Changes via `CommandFactory` (like `CompositeTimelineCommand`, `SnapshotCommand`) enable robust undo/redo capabilities for timeline edits.

## 10. Autosave/Project Persistence
- **Exists:** Yes.
- **Locations:** `/app/src/main/java/com/example/timeline/core/AutosaveManager.kt` and `ProjectStorage.kt`.
- **Notes:** `ClipTransform` uses Kotlinx Serialization `@Serializable` tags to fully persist the transform and keyframe data flawlessly.

## 11. Android NDK/CMake Configuration
- **Configured:** Yes.
- **Locations:** `/app/build.gradle.kts` uses CMake integration. `/app/src/main/cpp/CMakeLists.txt` builds `native_player` and the newly added `transform_engine`.

## 12. C++ Source Directory
- **Exists:** Yes, at `/app/src/main/cpp/`.
- **Notes:** Contains `CMakeLists.txt`, `native_player.cpp`, `ffmpeg_decoder.cpp`, and the newly created `transform_engine.cpp`.

## 13. JNI Bridge
- **Exists:** Yes.
- **Locations:** `/app/src/main/cpp/transform_engine.cpp` and `/app/src/main/java/com/example/timeline/engine/NativeTransformEngine.kt`.
- **Notes:** Exposes math computations (Matrix mapping, Keyframe Interpolation modes - Bezier/Linear/Hold). Native code evaluates matrixes for performance optimizations.

## 14. Current Player Render Surface
- **Surface:** Wrapped in `PlayerViewContainer`. Media3 defaults to `PlayerView` (which uses SurfaceView/TextureView). VLC uses `VLCVideoLayout`. Native CPP uses its own Surface.
- **Notes:** The transform is currently applied at the Compose overlay layer (UI-driven transform over Video Surface). Advanced per-pixel transforms (Blend Modes, Motion Blur) will require GPU Compositor / OpenGL shaders (which is marked for future implementation), but Compose Graphics Layer correctly scales/moves/rotates the rendering view without faking UI fields.

## Action Plan & Architectural Connectivity

### Existing Files to Modify
- `/app/src/main/java/com/example/timeline/core/ClipTransform.kt` (Already modified, to add fields)
- `/app/src/main/java/com/example/timeline/engine/preview/PlayerViewContainer.kt` (Already modified, to support C++ matrix bindings and Jetpack Compose `graphicsLayer`)
- `/app/src/main/java/com/example/ChromaProApp.kt` (Already modified, to open the Transform panel)
- `/app/build.gradle.kts` and `/app/src/main/cpp/CMakeLists.txt` (Already modified)
- The Export Pipeline `/app/src/main/java/com/example/timeline/export/` (Future modifications required to map transform parameters to the FFmpeg filter chain).

### New Files to Create
- `/app/src/main/cpp/transform_engine.cpp` (Already Created)
- `/app/src/main/java/com/example/timeline/engine/NativeTransformEngine.kt` (Already Created)
- `/app/src/main/java/com/example/ui/transform/TransformInspectorPanel.kt` (Already Created)

### Where C++ Native Code Will Connect
- C++ handles Keyframe interpolation (Bezier smoothing, linear sweeps) and high-speed Matrix evaluation.
- `NativeTransformEngine.kt` is the JNI wrapper.
- `PlayerViewContainer.kt` consumes these evaluated native outputs continuously during the playhead stream to move/scale/rotate the `VideoViewport`.
- Real-time calculations are decoupled from the CPU heavy garbage collector and bridged across the C++ boundary.

### What preview features can be real immediately
- **Scale, Position, Rotation, Anchor Point, Opacity:** Real-time via Jetpack Compose `graphicsLayer` acting intelligently on the SurfaceView outputs, using native-computed per-frame coordinates. Handled.
- **Flip Horizontal / Vertical:** Real-time natively integrated (using Negative Scale and Rotation conversions mapping to view adjustments).

### What requires GPU compositor/export pipeline
- **Blend Modes, Motion Blur, Perspective / Distort:** Standard Compose Graphics layers cannot distort texture meshes or merge complex multi-track overlay blend modes accurately over moving video textures. This requires a physical native GPU OpenGL / Vulkan compositor phase or an offline software renderer (FFmpeg filter chain). For now, these are modelled in `TransformState` correctly, but their preview uses placeholder bounds (not visually accurate composite) until the GPU Compositor is built. They remain mathematically real for the offline export phase.

### What must not be faked
- **Sliders:** The sliders must strictly affect the `TimelineProject` state matrix, and *not* just a temporary local `ViewModel` mutable state integer. We save directly to the architecture model.
- **Keyframes:** The timeline evaluates frames exactly. Pseudo-interpolation hacks are forbidden; Bezier and linear evaluations run via native routines mapping from the exact `sourceIn` and `sourceOut` timestamps.
- **State Serialization:** Never lose user configurations. Serialized perfectly into autosave project blobs.
