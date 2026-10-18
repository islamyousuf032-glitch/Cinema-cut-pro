package com.example.timeline.core.transform

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TransformKeyframe<T>(
    val id: String = UUID.randomUUID().toString(),
    val parameterId: String,
    val frame: Long,
    val value: T,
    val interpolation: TransformInterpolation = TransformInterpolation.LINEAR,
    val bezierHandleLeft: Float? = null,
    val bezierHandleRight: Float? = null
)
