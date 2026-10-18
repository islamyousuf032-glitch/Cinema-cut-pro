# Preview Engine Status

- **BrowserPreviewEngine**: Functional HTML5 DOM-based video player for maximum codec fallback tracking.
- **Media3PreviewPlayer**: ExoPlayer logic wired dynamically. Tracks time precisely via coroutines.
- **NativeCppPreviewEngine**: Interacts with C++ native components.
- **StillFramePreviewEngine**: Generates CPU-bound Bitmap instances to apply direct Color grading and pixel manipulations locally to provide high-quality real-time feedback when playback pauses.
- **Preview Render Pipeline**: Synchronizes transform states to rendering composables using `PlayerViewContainer.kt` and `ViewportTransformLayer.kt`.

## Status: COMPLETE
Capabilities respect boundaries. If real-time grading isn't permitted by a specific pipeline, it gracefully relies on `StillFramePreviewEngine` correctly handling logic.
