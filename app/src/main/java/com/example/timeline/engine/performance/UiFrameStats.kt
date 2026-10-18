package com.example.timeline.engine.performance

data class UiFrameStats(
    val uiFps: Float = 0f,
    val previewFps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val droppedFrames: Int = 0,
    val recompositionCount: Int = 0,
    val activeEngine: String = "Unknown",
    val memoryUsageMb: Float = 0f,
    val playheadUpdateRateMs: Float = 0f
)
