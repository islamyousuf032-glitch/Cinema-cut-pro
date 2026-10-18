package com.example.model.colorgrade

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ColorGradeLayerStack(
    val id: String = UUID.randomUUID().toString(),
    val layers: List<ColorGradeLayer> = listOf(
        ColorGradeLayer(name = "Node 1", type = ColorLayerType.PRIMARY_CORRECTION)
    ),
    val enabled: Boolean = true
)
