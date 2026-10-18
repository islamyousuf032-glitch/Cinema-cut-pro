# Device Compatibility & C++ Audit

## C++ / NDK Integration
23. **C++/NDK Configuration:** 
    - The project is fully configured for NDK with `app/src/main/cpp/CMakeLists.txt`.
    - Native build `.cxx` directories are successfully generated and compiled into `libvideoeditor.so` and related `.so` libraries.

24. **Native Functions Defined:** 
    Multiple engine bridges are mapped via `external fun`:
    - `NativePlayerBridge` (playback, seek, duration, dimensions)
    - `NativeTransformEngine` (matrix evaluation, keyframes, motion paths, hit testing, bounding box)
    - `NativeTimelineCore` (snap, x/frame conversions, hit test clip, thumbnail cells layout, visible range calculations)
    - `TimelineMathEngine` (pixel math, visibility checking)
    - `NativeExportTransformEngine` & `NativeExportCore` (compositing, audio muxing, watermark, rendering)
    - `MotionBlurEngine` (motion vectors, sample computing)

25. **Native Functions Called:**
    - Live timeline and player UI actively call functions like `nativeCalculateVisibleRange`, `nativeHitTestClip`, `nativeLayoutClips`, `nativeCreate`, `nativePlay`, `nativeGetPositionUs`, etc. 
    - The system is deeply coupled to C++ logic for math, transforms, rendering, and spatial UI hit testing.

## Potential Device Compatibility Risks
1. **GPU Rendering Issues (Samsung F23 / Exynos):** The Color Grade wheels use Jetpack Compose's `Brush.sweepGradient()`. On some Exynos devices and specific older Compose/Skia combinations, this gradient can silently fail and render as a solid black box.
2. **Missing Fallbacks:** There are no UI fallbacks for the color wheel (e.g., a static rasterized `.png` or `.webp` of a color wheel, or a simplified linear gradient) if the primary sweep gradient fails to compile on the device.
3. **NDK JNI Bridges:** `external` calls heavily rely on correct buffer layout, strict Float/Long handling, and memory addresses passing.
4. **OpenGL Shaders:** `AdjustmentShaderSource.kt` defines strict `#version 300 es` GLSL shaders. Devices that do not support GLES 3.0 will fail to compile the fragment shader for video adjustment application.
