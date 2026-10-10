package com.example.model.scopes

import android.graphics.Bitmap
import kotlin.math.roundToInt

class HistogramAnalyzer : ScopeAnalyzer {
    override fun analyze(bitmap: Bitmap, output: ScopeData) {
        output.histogramR.fill(0)
        output.histogramG.fill(0)
        output.histogramB.fill(0)
        output.histogramLuma.fill(0)

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)

            output.histogramR[r]++
            output.histogramG[g]++
            output.histogramB[b]++
            output.histogramLuma[luma]++
        }
    }
}
