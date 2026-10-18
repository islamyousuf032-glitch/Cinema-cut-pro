package com.example.timeline.core

enum class SnapTargetType(val priority: Int) {
    CLIP_BOUNDARY(1),
    PLAYHEAD(2),
    MARKER(3),
    BEAT_MARKER(4),
    GRID(5)
}

data class SnapTarget(
    val frame: Long,
    val type: SnapTargetType,
    val targetId: String? = null // e.g. clipId or markerId
)

data class SnapResult(
    val originalFrame: Long,
    val snappedFrame: Long,
    val snappedTo: SnapTargetType?,
    val targetId: String?,
    val distanceFrames: Long
)
