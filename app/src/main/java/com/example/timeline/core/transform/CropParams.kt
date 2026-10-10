package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
data class CropParams(
    val cropLeft: Float = 0f,
    val cropRight: Float = 0f,
    val cropTop: Float = 0f,
    val cropBottom: Float = 0f,
    val cropFeather: Float = 0f
)
