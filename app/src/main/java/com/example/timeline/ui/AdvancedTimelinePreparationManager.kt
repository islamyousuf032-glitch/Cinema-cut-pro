package com.example.timeline.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdvancedTimelinePreparationManager {
    private val _loadState = MutableStateFlow(AdvancedTimelineLoadState.STARTING)
    val loadState: StateFlow<AdvancedTimelineLoadState> = _loadState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val startTime = System.currentTimeMillis()

    suspend fun prepare() {
        try {
            _loadState.value = AdvancedTimelineLoadState.STARTING
            yield()
            
            _loadState.value = AdvancedTimelineLoadState.LOCKING_ORIENTATION
            yield()

            _loadState.value = AdvancedTimelineLoadState.LOADING_PROJECT
            yield()

            _loadState.value = AdvancedTimelineLoadState.LOADING_TIMELINE
            yield()

            _loadState.value = AdvancedTimelineLoadState.PREPARING_CLIP_LAYOUT
            yield()

            _loadState.value = AdvancedTimelineLoadState.PREPARING_THUMBNAILS
            yield()

            _loadState.value = AdvancedTimelineLoadState.PREPARING_MINI_VIEWPORT
            yield()

            // Ensure minimum 300ms transition time if it finishes very fast
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 300) {
                delay(300 - elapsed)
            }

            _loadState.value = AdvancedTimelineLoadState.READY
        } catch (e: Exception) {
            _error.value = e.message ?: "Unknown preparation error"
            _loadState.value = AdvancedTimelineLoadState.FAILED
        }
    }

    private suspend fun yield() {
        // Yield to allow UI to update between steps
        delay(30)
    }
}
