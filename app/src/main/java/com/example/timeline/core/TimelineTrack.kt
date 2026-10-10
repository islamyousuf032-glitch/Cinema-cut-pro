package com.example.timeline.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isVisible: Boolean = true,
    val isExpanded: Boolean = true,
    val height: Int = 100,
    val clips: List<TimelineClip> = emptyList(),
    val markers: List<TimelineMarker> = emptyList()
)
