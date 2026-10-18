package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
enum class KeyframeInterpolation {
    HOLD,
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BEZIER
}

@Serializable
data class AdjustmentKeyframe(
    val keyframeId: String,
    val parameterId: String, // String representation of the parameter, e.g., "exposureStops", "saturation"
    val frame: Long,
    val value: Float,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR
)
