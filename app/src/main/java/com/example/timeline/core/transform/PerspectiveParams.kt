package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
data class PerspectiveParams(
    val topLeftX: Float = 0f,
    val topLeftY: Float = 0f,
    val topRightX: Float = 1f,
    val topRightY: Float = 0f,
    val bottomLeftX: Float = 0f,
    val bottomLeftY: Float = 1f,
    val bottomRightX: Float = 1f,
    val bottomRightY: Float = 1f,
    val enabled: Boolean = false
)
