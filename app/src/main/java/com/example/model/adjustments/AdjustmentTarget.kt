package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
enum class TargetType {
    CLIP,
    TRACK,
    ADJUSTMENT_LAYER,
    GLOBAL
}

@Serializable
data class AdjustmentTarget(
    val type: TargetType,
    val targetId: String
)
