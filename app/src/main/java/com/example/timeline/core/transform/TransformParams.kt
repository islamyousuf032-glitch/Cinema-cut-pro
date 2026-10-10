package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
data class TransformParams(
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val opacity: Float = 1f
)
