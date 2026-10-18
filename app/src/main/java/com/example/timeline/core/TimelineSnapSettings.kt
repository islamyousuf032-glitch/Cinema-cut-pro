package com.example.timeline.core

data class TimelineSnapSettings(
    val isSnappingEnabled: Boolean = true,
    val snapToPlayhead: Boolean = true,
    val snapToClips: Boolean = true,
    val snapToMarkers: Boolean = true,
    val snapToGrid: Boolean = false,
    val gridIntervalFrames: Long = 24L,
    val snapThresholdPixels: Float = 10f
)
