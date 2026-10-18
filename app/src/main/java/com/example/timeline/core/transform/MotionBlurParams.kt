package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
data class MotionBlurParams(
    val enabled: Boolean = false,
    val shutterAngle: Float = 180f,
    val sampleCount: Int = 16,
    val strength: Float = 0.5f
)
