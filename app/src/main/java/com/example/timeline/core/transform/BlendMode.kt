package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    ADD,
    SUBTRACT,
    DARKEN,
    LIGHTEN,
    DIFFERENCE,
    SOFT_LIGHT
}
