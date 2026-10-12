package com.example.timeline.engine.preview

/** Playback diagnostics supplied by Media3's renderer analytics callbacks. */
data class PreviewPlaybackMetrics(
    val videoDecoderName: String? = null,
    val decoderInitializationDurationMs: Long? = null,
    val droppedVideoFrames: Long = 0L,
    val droppedFrameReportWindowMs: Long = 0L,
    val averageVideoFrameProcessingOffsetMs: Float? = null,
    val processedVideoFrameSamples: Long = 0L,
    val lateFrameProcessingWindows: Long = 0L
)
