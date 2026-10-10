package com.example.model.scopes

import android.graphics.Bitmap
import kotlin.math.roundToInt

class WaveformAnalyzer : ScopeAnalyzer {
    override fun analyze(bitmap: Bitmap, output: ScopeData) {
        output.waveformLuma.fill(0)
        output.paradeR.fill(0)
        output.paradeG.fill(0)
        output.paradeB.fill(0)

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Map x to 0..255 column
                val col = (x.toFloat() / width * 255).roundToInt().coerceIn(0, 255)
                val color = pixels[y * width + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)

                output.waveformLuma[col * 256 + luma]++
                output.paradeR[col * 256 + r]++
                output.paradeG[col * 256 + g]++
                output.paradeB[col * 256 + b]++
            }
        }
    }
}
