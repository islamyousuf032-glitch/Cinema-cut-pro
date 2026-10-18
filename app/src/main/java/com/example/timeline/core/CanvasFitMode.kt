package com.example.timeline.core

import kotlinx.serialization.Serializable

@Serializable
enum class CanvasFitMode {
    FIT_INSIDE,
    FILL_CROP,
    STRETCH,
    ORIGINAL_SIZE
}
