package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
data class AdjustmentLayer(
    val layerId: String,
    val name: String,
    val stack: AdjustmentStack,
    val startFrame: Long,
    val endFrame: Long,
    val trackId: String,
    val opacity: Float = 1f,
    val blendMode: String = "NORMAL",
    val visible: Boolean = true
)
