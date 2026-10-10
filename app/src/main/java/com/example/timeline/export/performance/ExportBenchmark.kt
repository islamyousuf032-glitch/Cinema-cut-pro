package com.example.timeline.export.performance

data class ExportBenchmark(
    val durationMs: Long,
    val renderFps: Float,
    val encodeFps: Float,
    val framesDropped: Long,
    val nativeStats: NativeExportStats
)
