package com.example.timeline.engine.native

data class NativeTimelineSnapResult(
    val snapped: Boolean,
    val snappedFrame: Long,
    val targetType: Int,
    val distancePixels: Float
)
