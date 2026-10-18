package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
enum class TransformFitMode {
    FIT,
    FILL,
    STRETCH,
    ORIGINAL_SIZE
}
