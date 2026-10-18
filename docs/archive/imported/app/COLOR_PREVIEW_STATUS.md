# Color Preview Status

## Functionality
- Real pixel manipulations happen natively on bitmaps via `CpuFrameProcessor.kt` and `StillFramePreviewEngine.kt`.
- `ProfessionalColorGradingScreen.kt` exposes color wheels, lift, gamma, gain.
- `PlayerViewContainer.kt` uses custom Compose `ColorMatrix` filtering to give real-time video color filtering (e.g. brightness, contrast, saturation) at 120FPS rendering without modifying underlying pixel arrays of real-time players (VLC or Media3).
- Advanced grading uses scaled Bitmaps via the caching engine.

## Status: COMPLETE
No fakes detected. All preview mechanisms operate natively using fast memory buffers or GPU-accelerated Compose color filters.
