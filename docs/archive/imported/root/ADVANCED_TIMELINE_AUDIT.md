# Advanced Timeline Audit

This document outlines the findings of the existing timeline architecture to prepare for the implementation of the Advanced Timeline Workspace.

## 1. Core Data Models
*   **TimelineProject**: Defined in `app/src/main/java/com/example/timeline/core/TimelineProject.kt`. Holds tracks, media assets, and project settings.
*   **TimelineClip**: Defined in `app/src/main/java/com/example/timeline/core/TimelineClip.kt`. Represents a media segment on the timeline with `timelineStart`, `timelineEnd`, `sourceIn`, `sourceOut`, etc.
*   **TimelineTrack (Layer Model)**: Defined in `app/src/main/java/com/example/timeline/core/TimelineTrack.kt`. Holds a list of `TimelineClip`s and track-level state (locked, hidden, etc.).

## 2. State Management
*   **selectedClipId**: Stored in `TimelineUiState` (`app/src/main/java/com/example/timeline/ui/TimelineUiState.kt`).
*   **playheadFrame**: Stored in two places for different purposes:
    *   `EditorViewModel` (`app/src/main/java/com/example/ui/editor/EditorViewModel.kt`) as `_playheadFrame`.
    *   Duplicated/Synchronized to `TimelineViewModel` (`app/src/main/java/com/example/timeline/ui/TimelineViewModel.kt`) to keep edit operations aware of the playhead.
*   **Current Timeline State**: The timeline uses `TimelineViewModel` (`uiState`) as the Single Source of Truth for the `TimelineProject`. 

## 3. UI and Gestures
*   **Basic Timeline UI**: Rendered via `CineTimelineCanvas.kt` and `TimelineScreen.kt` (`app/src/main/java/com/example/timeline/ui/`).
*   **Timeline Gestures**: Handled in `CineTimelineCanvas.kt` via pointer input mapping to `TimelineDragAction` and also using `TimelineGestureController.kt` (`app/src/main/java/com/example/timeline/ui/TimelineGestureController.kt`) for snap calculations.
*   **Playback Controller Updates**: The `TimelinePreviewController` updates the playhead frame via `onPlayheadAdvanced` callback defined in `ChromaProApp.kt`, which pushes updates to both `EditorViewModel` and `TimelineViewModel`.

## 4. Edit Logic & History
*   **Clip Move/Trim/Split Logic**: Core logic is pure Kotlin functions in `CoreEditEngine.kt` and `CoreTimelineEngine.kt` (`app/src/main/java/com/example/timeline/core/`). They take a `TimelineProject` and return a new `TimelineResult`.
*   **Undo/Redo System**: Managed by `TimelineHistoryManager.kt` (`app/src/main/java/com/example/timeline/core/TimelineHistoryManager.kt`), using `SnapshotCommand`s that are pushed by `TimelineViewModel`.
*   **Autosave/Project Persistence**: Handled by `AutosaveManager.kt`, `ProjectStorage.kt`, and `ProjectVersionManager.kt` (`app/src/main/java/com/example/timeline/core/`), triggered by `autosaveManager.markDirty(newProject)` in `TimelineViewModel`.

## 5. C++/NDK Integration
*   **Configured**: Yes, CMake is configured (`app/src/main/cpp/CMakeLists.txt`).
*   **Native Functions Existing**: Yes. JNI definitions are in `NativeTimelineCore.kt` (`app/src/main/java/com/example/timeline/engine/native/NativeTimelineCore.kt`). Implementations exist in C++ (e.g., `timeline_layout.cpp`, `timeline_snap.cpp`, `timeline_hit_test.cpp`). These functions are currently used for math, snapping, layout calculations, and thumbnails, rather than data mutation.

## 6. Code Reusability & Modification Plan
*   **Files to Reuse**:
    *   `TimelineProject.kt`, `TimelineClip.kt`, `TimelineTrack.kt`
    *   `TimelineViewModel.kt` (SSOT for timeline edits)
    *   `EditorViewModel.kt` (SSOT for playhead)
    *   `TimelinePreviewController.kt` (Playback engine)
    *   `CoreEditEngine.kt`, `CoreTimelineEngine.kt`
    *   `NativeTimelineCore.kt`
*   **Files to Modify**:
    *   `TimelineViewModel.kt` (To add missing Advanced Edit modes like Ripple, Roll, Slip, Slide which delegate to `CoreEditEngine` or native engines).
    *   `ChromaProApp.kt` (To handle Advanced Workspace toggling, which is already started).
*   **Files to Create**:
    *   `AdvancedTimelineScreen.kt` (Landscape root for Pro tools).
    *   `AdvancedTimelineCanvas.kt` (Derived from `CineTimelineCanvas.kt` but optimized and with expanded tools).
    *   `AdvancedTimelineToolbar.kt` (Pro tool selectors).
    *   `TimelineTool.kt` (Enum for Selection, Trim, Blade, Ripple, Roll, Slip, Slide).
    *   `MiniViewportContainer.kt` (Resizable floating or docked viewport).
*   **Files NOT to Touch**:
    *   Playback engine internals (`TimelinePreviewController`, `PreviewEngine`).
    *   Exporter logic (`NativeExportTransformEngine`, `ExportCore`).
    *   Color Grading/Match ViewModels (`ColorAdjustmentViewModel`, `ColorMatchViewModel`).
    *   Media Import logic (`MediaAnalyzer`, `ImportWorkManager`).

## 7. Current Data Flow
1. User performs gesture in `AdvancedTimelineCanvas`.
2. Gesture creates drag actions (e.g., `TimelineDragAction.TrimStart`).
3. Pointer release fires callback (e.g., `onRippleEdit`).
4. `TimelineViewModel` handles callback, applying advanced edit math (Ripple, Roll).
5. Math delegates to `CoreEditEngine`.
6. New `TimelineProject` is generated.
7. `TimelineViewModel` pushes to `TimelineHistoryManager` and updates `_uiState`.
8. UI recomposes with new project state.
9. `AutosaveManager` saves the new state to disk.

## 8. Exact Plan for Sharing TimelineProject
1. `ChromaProApp.kt` already instantiates a single `TimelineViewModel`.
2. When the user clicks "Pro Timeline", `showAdvancedTimeline` becomes true.
3. `ChromaProApp` conditionally renders `AdvancedTimelineScreen` instead of `EditorScreen`.
4. We pass the *same* `TimelineViewModel`, `EditorViewModel`, and `TimelinePreviewController` instance down to `AdvancedTimelineScreen`.
5. Because `TimelineViewModel` holds the `uiState` (`TimelineProject`), both the basic and advanced canvases will inherently edit the exact same data source, preserving clips and history without duplication.
6. The `MiniViewportContainer` inside the advanced workspace uses the exact same `VideoViewportSection` composable to reuse the rendering/playback pipeline without altering the core preview architecture.
