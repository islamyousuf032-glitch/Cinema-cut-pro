# Viewport Audit Report

## 1. Which file renders the main viewport
`app/src/main/java/com/example/timeline/ui/viewport/VideoViewport.kt` handles rendering the main canvas via `PlayerViewContainer.kt` and coordinates multiple layers on top of it. It is wrapped inside `app/src/main/java/com/example/ui/editor/VideoViewportSection.kt`.

## 2. Which file renders the viewport controls
`app/src/main/java/com/example/timeline/ui/viewport/ViewportControls.kt` contains the basic playback controls (Play, Safe, Guide). These are placed at the bottom within `VideoViewport.kt`.

## 3. Which file renders Fit/Fill/Stretch/Reset
`app/src/main/java/com/example/ui/editor/QuickTransformRow.kt` renders the visible row for Quick Transforms.
*Note: Legacy code for this also exists in `app/src/main/java/com/example/timeline/ui/viewport/ClipTransformControls.kt` although it's largely been removed from the main Viewport.*

## 4. Which file renders Play/Safe/Guide
`app/src/main/java/com/example/timeline/ui/viewport/ViewportControls.kt` renders these tools on top of/at the bottom of the viewport bounding area.

## 5. Which files render gear/settings icons
- **Inside VideoViewport.kt**: A working `IconButton` utilizing `Icons.Default.Settings` that toggles `showSettings = true`.
- **Inside QuickTransformRow.kt**: A duplicate `IconButton` utilizing `Icons.Filled.Settings` whose click action is completely empty/unimplemented.
- *Also, `EditorTopBar.kt` has a "Project Settings" button with a `PlayArrow` icon.*

## 6. Which file renders engine error overlays
`app/src/main/java/com/example/timeline/ui/viewport/VideoViewport.kt` renders Engine Error/Fallback messages ("Still preview. Native playback failed.", "Media Offline", etc.) using raw composables and occasionally calling `PreviewErrorOverlay()`.

## 7. Which file renders debug overlay text
- `app/src/main/java/com/example/timeline/ui/viewport/VideoViewport.kt` directly renders the debug overlay which prints first frame render status, URL, playhead info, engine badges, and experimental debug grade toggles inside the video area. 
- `app/src/main/java/com/example/timeline/engine/performance/EditorPerformanceOverlay.kt` also overlaps with frame time stats inside the top-start of the `VideoViewport`.

## 8. Which ViewModel owns viewport state
- `com.example.ui.editor.EditorViewModel` owns the player metadata through `previewSettings`.
- `com.example.timeline.ui.TimelineViewModel` provides clip data, playhead state parsing, bounds.

## 9. Which state controls selected preview engine
The selected preview engine state flows from `TimelinePreviewController.engineTypeFlow`, synchronized and configured through the `previewSettings` state originating from `EditorViewModel`.

## 10. Which code opens the viewport settings menu
A local `var showSettings by remember { mutableStateOf(false) }` resides in `VideoViewport.kt`, which conditionally wraps `ViewportSettingsMenu`.

## 11. Whether the settings menu is connected to ViewModel or local state
It is a mix. It toggles visibility using a local UI state (`showSettings`), but the actual data (quality mode, proxy settings, engine fallback) writes to and reads from `EditorViewModel` using event callbacks.

## 12. Whether old overlay code still exists
Yes. Legacy UI functions like `ClipTransformControls.kt` still exist. `VideoViewport.kt` features significant logic nesting for various fallback modes (`ProxyStatus.GENERATING`, error handling screens with direct buttons) which clutter the viewport.

## Analysis & Recommendations
**Duplicate Settings Locations:**
Settings icons are erroneously distributed between `VideoViewport` (working) and `QuickTransformRow` (broken).

**Overlays currently inside viewport:**
1. Debug frame/URL text box.
2. Debug grade toggles.
3. Timecode overlay.
4. Top metadata text (`Project Name | Resolution | FPS | ColorSpace`).
5. Engine badge text.
6. Settings Gear Icon.
7. Engine Error messages + Action buttons ("Generate Proxy", "Relink Media").
8. Performance Monitor FPS stats overlay.
9. `SafeAreaOverlay` and `CanvasGuideOverlay`.

**Controls that must be moved outside viewport:**
- Project Metadata text (Resolution, FPS).
- Settings Gear Icon.
- Warning Action Buttons (Relink, Generate Proxy).
- Quick Debug / Grade toggles.
- Standard controls (`ViewportControls` Play/Safe/Guide).

**Files to Edit:**
- `app/src/main/java/com/example/timeline/ui/viewport/VideoViewport.kt`
- `app/src/main/java/com/example/timeline/ui/viewport/ViewportControls.kt`
- `app/src/main/java/com/example/ui/editor/QuickTransformRow.kt`
- `app/src/main/java/com/example/ui/editor/VideoViewportSection.kt`

**Files Not to Touch:**
- Core engine files (`NativeTransformEngine.cpp`, `PlayerViewContainer.kt`, etc.).
- Color & Transform tools (`TransformHandleOverlay.kt`, color view models).
- Import logics.

**Exact Current Layout Hierarchy:**
```
EditorScreen
 ├── VideoViewportSection
 │    ├── VideoViewport (Black background aspect relative)
 │    │    ├── Metadata Toolbar (Text + Settings Icon)
 │    │    ├── Box (Main Input bounds)
 │    │    │    ├── ProjectCanvas
 │    │    │    │    ├── [StillFrameFallback OR PlayerViewContainer]
 │    │    │    │    ├── Error/Fallback Overlays & Action Buttons
 │    │    │    │    ├── TransformHandleOverlay
 │    │    │    │    ├── MotionPathRenderer
 │    │    │    │    ├── Debug Info Overlay + Color Toggles
 │    │    │    │    └── Engine Badge / SafeArea / Guides
 │    │    │    ├── Timecode Overlay Text
 │    │    │    └── EditorPerformanceOverlay
 │    │    └── ViewportControls (Play / Safe / Guide)
 ├── QuickTransformRow (Contains Fit/Fill/Stretch and duplicate non-functional Settings Icon)
 ├── EditorToolTabs
 └── EditorBottomPanel
```
