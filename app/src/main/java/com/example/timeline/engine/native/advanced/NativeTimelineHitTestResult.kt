package com.example.timeline.engine.native.advanced

data class NativeTimelineHitTestResult(
    val hit: Boolean,
    val clipId: Int = -1,
    val trackIndex: Int = -1
)
