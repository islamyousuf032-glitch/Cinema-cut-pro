package com.example.timeline.core

import kotlinx.serialization.Serializable
import java.util.UUID
import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.TargetType
import com.example.timeline.core.transform.ClipTransform

@Serializable
data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val mediaId: String,
    val name: String,
    val sourceIn: Long,
    val sourceOut: Long,
    val timelineStart: Long,
    val isLocked: Boolean = false,
    val isEnabled: Boolean = true,
    val type: ClipType = ClipType.MEDIA,
    val volume: Float = 1.0f,
    val metadata: Map<String, String> = emptyMap(),
    val transform: ClipTransform = ClipTransform(),
    val adjustments: AdjustmentStack = AdjustmentStack("stack_$id", TargetType.CLIP, id),
    val colorGrade: com.example.model.colorgrade.ColorGradeStack = com.example.model.colorgrade.ColorGradeStack("grade_$id", com.example.model.colorgrade.ColorGradeTarget.CLIP, id),
    val colorLayers: com.example.model.colorgrade.ColorGradeLayerStack = com.example.model.colorgrade.ColorGradeLayerStack(),
    val markers: List<TimelineMarker> = emptyList()
) {
    val duration: Long
        get() = sourceOut - sourceIn

    val timelineEnd: Long
        get() = timelineStart + duration

    init {
        require(duration >= 0) { "Clip duration cannot be negative" }
        require(sourceOut >= sourceIn) { "sourceOut must be >= sourceIn" }
        require(timelineStart >= 0) { "timelineStart must be >= 0" }
    }
}
