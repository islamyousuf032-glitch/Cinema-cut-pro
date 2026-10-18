package com.example.model.scopes

import android.graphics.Bitmap
import kotlin.math.roundToInt

class VectorscopeAnalyzer : ScopeAnalyzer {
    override fun analyze(bitmap: Bitmap, output: ScopeData) {
        output.vectorscope.fill(0)
        
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (color in pixels) {
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            
            // Basic YCbCr 
            // Cb = -0.1146 * r - 0.3854 * g + 0.5 * b
            // Cr = 0.5 * r - 0.4542 * g - 0.0458 * b
            val cb = (-0.1146f * r - 0.3854f * g + 0.5f * b)
            val cr = (0.5f * r - 0.4542f * g - 0.0458f * b)

            // cb, cr are approx -0.5 to 0.5
            // map mapping -0.7 to 0.7 into 0..255 for plot
            // val minMap = -0.6f
            // val maxMap = 0.6f
            
            val scaledCb = (cb * 1.5f + 0.5f).coerceIn(0f, 1f)
            val scaledCr = (cr * 1.5f + 0.5f).coerceIn(0f, 1f)
            
            val x = (scaledCb * 255).roundToInt().coerceIn(0, 255)
            val y = (scaledCr * 255).roundToInt().coerceIn(0, 255)
            
            output.vectorscope[x * 256 + y]++
        }
    }
}
