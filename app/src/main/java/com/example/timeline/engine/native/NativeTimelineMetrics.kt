package com.example.timeline.engine.native

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NativeTimelineMetrics {
    private val _lastLayoutTimeMs = MutableStateFlow(0L)
    val lastLayoutTimeMs = _lastLayoutTimeMs.asStateFlow()

    fun reportLayoutTime(timeMs: Long) {
        _lastLayoutTimeMs.value = timeMs
    }
}
