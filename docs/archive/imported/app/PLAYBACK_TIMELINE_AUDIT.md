# Playback & Timeline Sync Audit

## 1. Active Preview Player Ownership
The active preview player is owned by `TimelinePreviewController`. It instantiates and holds a reference to a `PreviewEngine` implementation.

## 2. Active Video Player
The player currently playing video is **ExoPlayer/Media3** (via `Media3FallbackPreviewEngine`). This is selected when `AUTO` or `MEDIA3_FALLBACK` is used.

## 3. Engine Options in UI
The following engine options are exposed in the `ViewportSettingsMenu` UI:
- `AUTO`
- `MEDIA3_FALLBACK`
- `STILL_FRAME`
- `PROXY_PREVIEW`
- `BROWSER`

## 4. Working Engine Options
- `AUTO` (defaults to Media3)
- `MEDIA3_FALLBACK` (fully working for moving video)
- `STILL_FRAME` (works for extracting static bitmaps)

## 5. Fake or Incomplete Engine Options
- `PROXY_PREVIEW`: Incomplete/fake implementation.
- `BROWSER`: Fake implementation.
- `NATIVE_CPP`: Missing from UI dropdown, incomplete.
- `VLC_NATIVE`: Incomplete (missing real-time grading, hidden from UI).

## 6. Play/Pause Handling
Play/pause is handled by `TimelinePreviewController.play()` and `pause()`. The UI triggers this via `TimelineViewModel.togglePlay()`, which updates an `isPlaying` state that `VideoViewportSection` observes.

## 7. Player Current Time
The player's current time is available at `previewEngine.currentPositionUs` and via the `engine.onTimeChanged` callback.

## 8. PlayheadFrame Storage
The playhead frame is stored in two places:
- `EditorViewModel._playheadFrame` (StateFlow)
- `TimelineViewModel._playheadFrame` (StateFlow)

## 9. Timeline UI Observation
Yes, the Timeline UI observes the correct playhead frame via `val playheadState = editorViewModel.playheadFrame.collectAsState()` in `TimelineScreen`.

## 10. Why Timeline Playhead Does Not Move During Playback
**Missing Connection / Recomposition Bug:**
The playhead state is passed to the timeline canvas as a lambda: `playheadProvider = { playheadState.value }`. Jetpack Compose memoizes this lambda. Because the arguments to the timeline canvas composable (like the lambda instance and `uiState`) do not change, Compose skips recomposing the canvas child. The lambda is never re-evaluated, so the canvas does not redraw with the new frame, leaving the playhead visually frozen despite the video playing.

## 11. File Rendering Current Timeline
The current timeline is rendered by `CinematicTimelineCanvas.kt`.

## 12. Files to Modify/Replace for New Timeline UI
- `TimelineScreen.kt` (to update playhead reading strategy and layout)
- `CinematicTimelineCanvas.kt` (to replace with the new premium UI)

## 13. C++/NDK Configuration
Yes, C++/NDK is already configured via `CMakeLists.txt`.

## 14. Native C++ Timeline Code
Yes, native C++ code exists (`timeline_math.cpp`, `timeline_math.h`).

## Summary Report
- **Current active playback engine:** Media3/ExoPlayer (`Media3FallbackPreviewEngine`).
- **Current playhead state source:** `EditorViewModel.playheadFrame`.
- **Missing connection causing timeline freeze:** Compose recomposition skip due to memoized lambda `playheadProvider`. The Canvas does not observe the state read in its `onDraw` phase, so it fails to invalidate.
- **Old timeline UI files:** `TimelineCanvas.kt`, `TimelineClipView.kt`, `TimelineRuler.kt` (removed/replaced).
- **Engine options that must be hidden/disabled until real:** `PROXY_PREVIEW`, `BROWSER`, `VLC_NATIVE`.
- **Files to modify:** `TimelineScreen.kt`, `CinematicTimelineCanvas.kt`.
- **Files not to touch:** `TimelinePreviewController.kt`, `Media3PreviewPlayer.kt` (Playback is working, do not break it).
