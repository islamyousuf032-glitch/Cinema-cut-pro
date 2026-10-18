package com.example.timeline.export.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(
        val stage: String,
        val renderedFrames: Long,
        val totalFrames: Long,
        val percent: Float,
        val elapsedMs: Long,
        val estimatedRemainingMs: Long
    ) : ExportState()
    data class Completed(val video: ExportedVideo) : ExportState()
    data class Error(val message: String) : ExportState()
}

object ExportProgressRepository {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun updateState(state: ExportState) {
        _exportState.value = state
    }
}
