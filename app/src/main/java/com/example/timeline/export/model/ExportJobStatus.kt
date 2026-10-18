package com.example.timeline.export.model

enum class ExportJobStatus {
    QUEUED,
    VALIDATING,
    PREPARING,
    RENDERING_VIDEO,
    RENDERING_AUDIO,
    ENCODING,
    MUXING,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELLED
}
