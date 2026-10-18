package com.example.model.adjustments.engine

import android.graphics.Bitmap

data class ScopeData(
    val histogramR: IntArray = IntArray(256),
    val histogramG: IntArray = IntArray(256),
    val histogramB: IntArray = IntArray(256),
    val histogramLuma: IntArray = IntArray(256)
)

object ScopesEngine {
    fun generateScopes(bitmap: Bitmap): ScopeData {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val data = ScopeData()

        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

            data.histogramR[r]++
            data.histogramG[g]++
            data.histogramB[b]++
            data.histogramLuma[luma]++
        }
        return data
    }
}
