package com.example.timeline.export.model

data class ExportJob(
    val jobId: String,
    val projectId: String,
    val settings: ExportSettings,
    val status: ExportJobStatus = ExportJobStatus.QUEUED,
    val progressPercent: Float = 0f,
    val renderedFrames: Long = 0,
    val totalFrames: Long = 0,
    val elapsedMs: Long = 0,
    val estimatedRemainingMs: Long = 0,
    val currentStage: String = "",
    val outputUri: String? = null,
    val errorMessage: String? = null,
    val technicalError: String? = null
)
