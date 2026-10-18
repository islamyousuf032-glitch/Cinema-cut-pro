package com.example.timeline.export.model

data class ExportReport(
    val jobId: String,
    val projectId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val totalFramesRendered: Long,
    val averageFps: Float,
    val finalFileSizeBytes: Long,
    val outputUri: String,
    val settingsUsed: ExportSettings,
    val backendUsed: ExportBackendType,
    val warnings: List<String> = emptyList()
)
