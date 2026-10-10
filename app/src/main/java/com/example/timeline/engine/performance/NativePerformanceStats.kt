package com.example.timeline.engine.performance

data class NativePerformanceStats(
    val nativeTransformTimeMs: Float = 0f,
    val nativeInterpolatorTimeMs: Float = 0f,
    val nativeRenderTimeMs: Float = 0f
)
