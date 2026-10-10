package com.example.timeline.export.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportResolution(
    val width: Int,
    val height: Int
) {
    fun getPresetName(): String? = when {
        width == 1920 && height == 1080 -> "1080p"
        width == 3840 && height == 2160 -> "4K"
        width == 7680 && height == 4320 -> "8K"
        width == 1280 && height == 720 -> "720p"
        else -> null
    }
}
