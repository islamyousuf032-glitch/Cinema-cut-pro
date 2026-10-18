package com.example.timeline.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
class Point(val x: Float, val y: Float)

@Serializable
data class Keyframe<T>(
    val frame: Long,
    val value: T,
    val interpolation: InterpolationMode = InterpolationMode.LINEAR
)

@Serializable
enum class InterpolationMode {
    LINEAR,
    BEZIER,
    HOLD
}

@Serializable
data class TransformState(
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val positionX: Float = 0.0f,
    val positionY: Float = 0.0f,
    val rotation: Float = 0.0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val opacity: Float = 1.0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeft: Float = 0.0f,
    val cropTop: Float = 0.0f,
    val cropRight: Float = 0.0f,
    val cropBottom: Float = 0.0f,
    val perspectiveTopLeft: Point? = null,
    val perspectiveTopRight: Point? = null,
    val perspectiveBottomLeft: Point? = null,
    val perspectiveBottomRight: Point? = null,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val motionBlurEnabled: Boolean = false,
    val motionBlurAmount: Float = 0.5f,
    
    // Keyframes lists
    val positionKeyframes: List<Keyframe<Point>> = emptyList(),
    val scaleKeyframes: List<Keyframe<Point>> = emptyList(),
    val rotationKeyframes: List<Keyframe<Float>> = emptyList(),
    val opacityKeyframes: List<Keyframe<Float>> = emptyList()
)

@Serializable
enum class BlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN,
    HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION
}
