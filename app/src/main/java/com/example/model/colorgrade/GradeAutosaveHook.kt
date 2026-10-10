package com.example.model.colorgrade

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object GradeAutosaveHook {
    private var debounceJob: Job? = null
    private const val DEBOUNCE_MS = 2000L

    /**
     * Called continuously while dragging sliders/wheels.
     * Prevents saving on every micro-adjustment.
     */
    fun onGradeChanged(scope: CoroutineScope, saveAction: () -> Unit) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            saveAction()
        }
    }

    /**
     * Called when a drag gesture ends (e.g. pointer up).
     * Bypasses debounce to save immediately.
     */
    fun onGestureEnded(scope: CoroutineScope, saveAction: () -> Unit) {
        debounceJob?.cancel()
        scope.launch {
            saveAction()
        }
    }
}
