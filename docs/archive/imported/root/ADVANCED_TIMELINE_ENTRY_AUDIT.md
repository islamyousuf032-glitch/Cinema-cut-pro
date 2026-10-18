# Advanced Timeline Entry Audit

## Findings

1. **Which file renders the "Advanced Timeline" button:**
   `app/src/main/java/com/example/ui/EditorTopBar.kt` (line 106).

2. **Why it was placed in the top bar/project title area:**
   It was placed there as a quick integration point to access the landscape timeline without building a proper navigation drawer, bottom bar, or dedicated workspace launcher.

3. **Which function runs when the button is tapped:**
   The `onAdvancedTimelineClick` lambda is triggered, which sets the `showAdvancedTimeline` boolean state to `true` inside `ChromaProApp.kt`.

4. **Whether it only changes orientation or actually navigates:**
   It does not actually navigate (no `NavHost` or `Intent` is used). It conditionally renders the `AdvancedTimelineRoute` composable and calls `OrientationController.requestLandscape(context)`, which forces a programmatic orientation change.

5. **Whether an AdvancedTimelineScreen route exists:**
   A `AdvancedTimelineRoute` composable exists in `AdvancedTimelineRoute.kt`, but it acts as a wrapper rather than a true Jetpack Navigation route.

6. **Whether an AdvancedTimelineActivity exists:**
   No. Everything runs within the single `MainActivity`.

7. **Whether orientation is locked using requestedOrientation:**
   Yes, `OrientationController.requestLandscape(context)` sets `Activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE` inside a `DisposableEffect` in `AdvancedTimelineScreen.kt`.

8. **Whether orientation is restored too early:**
   Yes. Because the orientation change triggers an Activity recreation, `AdvancedTimelineScreen` leaves the composition, triggering its `onDispose` block, which immediately restores the orientation back to portrait.

9. **Whether Activity recreation loses state:**
   Yes. `showAdvancedTimeline` is defined as `var showAdvancedTimeline by remember { mutableStateOf(false) }` in `ChromaProApp.kt`. Standard `remember` does not survive Activity recreation. 

10. **Whether EditorViewModel is shared or recreated:**
    `EditorViewModel` and `TimelineViewModel` are created using `viewModel()` in `ChromaProApp.kt`, so they survive Activity recreation.

11. **Whether timeline/project state is saved before opening Advanced Timeline:**
    The logical state is kept in the ViewModels (which survive), but UI-specific states (like `showAdvancedTimeline`) and non-ViewModel instances are lost.

12. **Whether player surface is released safely before orientation change:**
    No. `TimelinePreviewController` is instantiated with a standard `remember` and does not have a `release()` call in the `ChromaProApp` lifecycle. The underlying native media engine (LibVLC/Media3) surfaces are leaked during the recreation.

13. **What causes the crash:**
    The unreleased `TimelinePreviewController` and its native media surfaces leak memory and native resources during the unexpected Activity recreation cycle, leading to a crash.

14. **What causes the return to the previous screen:**
    The forced orientation change triggers an Activity recreation. The recreated Activity initializes `showAdvancedTimeline` back to its default `false`. The UI renders the portrait `EditorScreen` instead of `AdvancedTimelineScreen`. The old `AdvancedTimelineScreen` is disposed, triggering a second orientation change back to portrait.

---

## Conclusion & Recommendations

**Root Cause:**
The application forces a landscape orientation change programmatically without declaring `android:configChanges="orientation|screenSize|keyboardHidden"` in the `AndroidManifest.xml`. This causes an immediate Activity recreation. Because the flag controlling the UI (`showAdvancedTimeline`) is not saved in a `rememberSaveable`, it resets to `false`, aborting the transition. The heavy native media player is not released cleanly during this violent recreation cycle, causing a crash.

**Files to modify:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/ChromaProApp.kt`
- (Optional) Create a proper navigation framework.

**Recommended Fix:**
There are two main approaches:
* **Option A: Separate `AdvancedTimelineActivity`**
  Launch a new Activity forced to landscape in the Manifest.
* **Option B: Compose navigation route with orientation lock**
  Add `configChanges="orientation|screenSize|keyboardHidden|screenLayout"` to `AndroidManifest.xml` so the Activity is *not* recreated on rotation. Keep the state in Compose or ViewModels, and manage the UI swap safely.

**Which option is safer for this app:**
**Option B** is significantly safer and easier for this specific app. The app relies heavily on shared state between `TimelineViewModel`, `EditorViewModel`, and the `TimelinePreviewController`. Passing this heavy, real-time media state across Activity boundaries (Option A) would require a massive architectural rewrite (e.g., singleton media engines, heavy database serialization). By preventing Activity recreation (Option B), the view models and the media player survive the rotation seamlessly, eliminating the crash and state-loss issues immediately.
