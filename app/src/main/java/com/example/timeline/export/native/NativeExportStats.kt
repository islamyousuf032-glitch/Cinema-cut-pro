package com.example.timeline.export.native

data class NativeExportStats(
    val framesProcessed: Int,
    val totalRenderTimeMs: Int,
    val audioSamplesMixed: Int
)
