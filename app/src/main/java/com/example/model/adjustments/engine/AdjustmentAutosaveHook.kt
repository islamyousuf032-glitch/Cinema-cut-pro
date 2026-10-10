package com.example.model.adjustments.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdjustmentAutosaveHook(
    private val scope: CoroutineScope,
    private val debounceTimeMs: Long = 1000L,
    private val onSave: () -> Unit
) {
    private var saveJob: Job? = null

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(debounceTimeMs)
            onSave()
        }
    }

    fun forceSave() {
        saveJob?.cancel()
        onSave()
    }
}
