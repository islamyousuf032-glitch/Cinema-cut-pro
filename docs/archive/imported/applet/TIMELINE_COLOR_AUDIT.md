# Timeline & Color Grade Audit

## Timeline Architecture
1. **Project Media Section Render:** `app/src/main/java/com/example/timeline/ui/MediaBinPanel.kt`
2. **Timeline UI Render:** `app/src/main/java/com/example/timeline/ui/TimelineScreen.kt` (Parent) and `app/src/main/java/com/example/timeline/ui/CineTimelineCanvas.kt` (Core drawing).
3. **Track Headers Render:** Inside `app/src/main/java/com/example/timeline/ui/CineTimelineCanvas.kt` (DrawScope logic).
4. **Default Tracks Creation:** `app/src/main/java/com/example/timeline/ui/TimelineViewModel.kt` (creates initial tracks).
5. **Media Import Function:** `enqueueMediaAnalysis` inside `TimelineViewModel.kt`.
6. **Timeline Add Media Function:** `addClipFromMedia` inside `TimelineViewModel.kt` which calls `AddToTimelineController.createAddClipCommand`.
7. **Track Static/Dynamic state:** Dynamic in the core model (`TimelineProject.tracks`), and engine supports `addTrack()`. The logic in `AddToTimelineController` dynamically adds video or audio tracks if none are unlocked/available when a clip is dragged. However, there is no explicit user-facing "Add Track" button.
8. **Layer Workflow for Media:** Yes, if an existing track is full or locked, the controller automatically provisions a new track. 
9. **Clip Name Render Location:** Legacy UI `FilmStripClipView.kt` hardcodes it as `"Clip ${clip.id.take(4)}"`. `CineTimelineCanvas.kt` renders it inside `drawClip()`. 
10. **Track/Layer Rename:** The engine supports `renameTrack` (`CoreTimelineEngine.renameTrack`), but there is no UI implementation hooked up to use it.
11. **Playhead State Sharing:** Yes, shared via `timelineViewModel.syncPlayheadFromPlayer` linking the timeline UI and video player.
12. **UI Duplication:** `FilmStripClipView.kt` is present but largely superseded by `CineTimelineCanvas.kt` which handles all canvas-based timeline drawing.

## Viewport Controls
13. **Fit/Fill/Stretch/Reset Controls:** `app/src/main/java/com/example/ui/editor/viewport/ViewportControlsSection.kt` and `QuickTransformRow.kt`.
14. **Play/Safe/Guide Controls:** `app/src/main/java/com/example/ui/editor/viewport/PlaybackControlsSection.kt`.
15. **Compaction:** Both sections are separate rows inside `app/src/main/java/com/example/ui/editor/VideoViewportSection.kt`. They can easily be consolidated into a single compact overlay row.

## Color Grading
16. **Color Wheel Renderer:** `app/src/main/java/com/example/ui/colorgrade/ColorWheelControl.kt`.
17. **Renderer Technique:** Jetpack Compose `Canvas` using `Brush.sweepGradient`.
18. **Samsung Galaxy F23 Black Screen Issue:** `Brush.sweepGradient` has known compatibility issues on specific Android/Compose versions on Exynos/Mali GPUs, resulting in the canvas rendering black or failing.
19. **Fallback Renderer:** There is no fallback renderer (like a static Bitmap or basic lines) implemented if the sweep gradient shader fails.
20. **State Management:** Sliders and wheels trigger `updateColorGradeLive(newGrade)` which updates the actual underlying `ColorGradeStack`.
21. **Preview Effect:** Yes, live updates affect the Video Player Preview real-time.
22. **Curve Accuracy:** Curves utilize a genuine evaluator math engine (`CurveProcessor.kt`) for local reference, rather than just faking a graph UI. OpenGL GLSL (`AdjustmentShaderSource.kt`) manages the hardware shader application.
