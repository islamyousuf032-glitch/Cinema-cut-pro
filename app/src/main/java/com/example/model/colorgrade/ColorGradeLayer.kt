package com.example.model.colorgrade

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ColorGradeLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ColorLayerType,
    val enabled: Boolean = true,
    val opacity: Float = 1f,
    val grade: ColorGradeStack = ColorGradeStack(targetType = ColorGradeTarget.CLIP, targetId = "layer")
)
