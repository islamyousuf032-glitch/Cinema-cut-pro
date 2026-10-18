package com.example.timeline.export.model

import kotlinx.serialization.Serializable

@Serializable
data class WatermarkSettings(
    val enabled: Boolean = false,
    val isText: Boolean = true,
    val text: String = "ChromaPro",
    val imageUri: String? = null,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val opacity: Float = 0.5f,
    val sizePercent: Float = 10f, // 10% of screen height/width
    val marginPercent: Float = 5f
)

@Serializable
enum class WatermarkPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}
