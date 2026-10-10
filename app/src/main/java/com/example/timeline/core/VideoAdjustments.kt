package com.example.timeline.core

import kotlinx.serialization.Serializable

@Serializable
data class VideoAdjustments(
    // Exposure / Brightness / Contrast Tone
    val exposure: Float = 0f, // -2.0 to 2.0 (stops)
    val brightness: Float = 0f, // -100 to 100
    val contrast: Float = 1f, // 0.0 to 2.0 (1 is neutral)
    val highlights: Float = 0f, // -100 to 100
    val shadows: Float = 0f, // -100 to 100
    val whites: Float = 0f, // -100 to 100
    val blacks: Float = 0f, // -100 to 100
    
    // Color
    val saturation: Float = 1f, // 0.0 to 2.0 (1 is neutral)
    val vibrance: Float = 0f, // -100 to 100
    val temperature: Float = 0f, // -100 to 100 (cold to warm)
    val tint: Float = 0f, // -100 to 100 (green to magenta)
    
    // Detail
    val sharpness: Float = 0f, // 0 to 100
    val clarity: Float = 0f, // -100 to 100
    
    // Effects
    val fade: Float = 0f, // 0 to 100
    val filmGrain: Float = 0f, // 0 to 100
    val vignetteAmount: Float = 0f, // -100 to 100
    val vignetteMidpoint: Float = 50f, // 0 to 100
    val dehaze: Float = 0f, // -100 to 100
    
    // RGB Balance (Simplified Color Wheels)
    val redMidtones: Float = 0f, // -100 to 100
    val greenMidtones: Float = 0f, // -100 to 100
    val blueMidtones: Float = 0f // -100 to 100
) {
    fun isNeutral(): Boolean {
        return this == VideoAdjustments()
    }
}
