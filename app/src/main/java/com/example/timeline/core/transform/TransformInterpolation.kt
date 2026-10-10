package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
enum class TransformInterpolation {
    HOLD,
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BEZIER
}
