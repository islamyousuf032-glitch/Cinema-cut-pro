package com.example.timeline.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MarkerType {
    NOTE, SCENE, BEAT, DIALOGUE, ACTION, VFX, CUT_POINT
}

@Serializable
data class TimelineMarker(
    val id: String = UUID.randomUUID().toString(),
    val frame: Long,
    val name: String = "",
    val description: String = "",
    val color: String = "#FFFFFF",
    val type: MarkerType = MarkerType.NOTE,
    val duration: Long? = null
)
