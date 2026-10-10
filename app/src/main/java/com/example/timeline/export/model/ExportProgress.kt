package com.example.timeline.export.model

data class ExportProgress(
    val jobId: String,
    val status: ExportJobStatus,
    val progressPercent: Float,
    val renderedFrames: Long,
    val totalFrames: Long,
    val elapsedMs: Long,
    val estimatedRemainingMs: Long,
    val currentStage: String,
    val errorMessage: String? = null
)
