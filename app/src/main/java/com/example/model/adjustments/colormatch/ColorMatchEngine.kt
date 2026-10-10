package com.example.model.adjustments.colormatch

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.adjustments.Vector3
import com.example.model.adjustments.VideoAdjustmentParams
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ColorMatchParams(
    val matchExposure: Boolean = true,
    val matchContrast: Boolean = true,
    val matchWhiteBalance: Boolean = true,
    val matchSaturation: Boolean = true,
    val matchStrength: Float = 1.0f // 0.0 to 1.0
)

data class ImageAnalysisResult(
    val averageLuminance: Float,
    val contrast: Float,
    val averageColor: Vector3,
    val averageSaturation: Float,
    val shadowsColor: Vector3,
    val midtonesColor: Vector3,
    val highlightsColor: Vector3
)

object ShotMatchAnalyzer {
    fun analyze(bitmap: Bitmap): ImageAnalysisResult {
        var totalLuma = 0.0
        var totalR = 0.0
        var totalG = 0.0
        var totalB = 0.0
        var totalSat = 0.0

        var shadowCount = 0
        var midCount = 0
        var highCount = 0

        var sR = 0.0; var sG = 0.0; var sB = 0.0
        var mR = 0.0; var mG = 0.0; var mB = 0.0
        var hR = 0.0; var hG = 0.0; var hB = 0.0

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Subsample for performance if needed, but doing all for now (acceptable if bitmap is scaled down)
        // Usually we want to scale down the bitmap before analysis to ~256x256
        val step = max(1, pixels.size / 65536)

        var count = 0
        val hsl = FloatArray(3)
        for (i in pixels.indices step step) {
            val color = pixels[i]
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0

            val maxC = max(max(r, g), b)
            val minC = min(min(r, g), b)
            val luma = (maxC + minC) / 2.0
            var sat = 0.0
            if (maxC != minC) {
                sat = if (luma < 0.5) (maxC - minC) / (maxC + minC) else (maxC - minC) / (2.0 - maxC - minC)
            }

            totalLuma += luma
            totalR += r
            totalG += g
            totalB += b
            totalSat += sat

            if (luma < 0.33) {
                sR += r; sG += g; sB += b; shadowCount++
            } else if (luma > 0.66) {
                hR += r; hG += g; hB += b; highCount++
            } else {
                mR += r; mG += g; mB += b; midCount++
            }
            count++
        }

        if (count == 0) return ImageAnalysisResult(0f, 0f, Vector3.ZERO, 0f, Vector3.ZERO, Vector3.ZERO, Vector3.ZERO)

        val avgLuma = (totalLuma / count).toFloat()
        
        // Calculate contrast (std dev of luma)
        var lumaVariance = 0.0
        for (i in pixels.indices step step) {
             val color = pixels[i]
             val r = Color.red(color) / 255.0
             val g = Color.green(color) / 255.0
             val b = Color.blue(color) / 255.0
             val luma = (max(max(r, g), b) + min(min(r, g), b)) / 2.0
             lumaVariance += (luma - avgLuma).pow(2)
        }
        val contrast = Math.sqrt(lumaVariance / count).toFloat()

        return ImageAnalysisResult(
            averageLuminance = avgLuma,
            contrast = contrast,
            averageColor = Vector3((totalR / count).toFloat(), (totalG / count).toFloat(), (totalB / count).toFloat()),
            averageSaturation = (totalSat / count).toFloat(),
            shadowsColor = if (shadowCount > 0) Vector3((sR / shadowCount).toFloat(), (sG / shadowCount).toFloat(), (sB / shadowCount).toFloat()) else Vector3.ZERO,
            midtonesColor = if (midCount > 0) Vector3((mR / midCount).toFloat(), (mG / midCount).toFloat(), (mB / midCount).toFloat()) else Vector3.ZERO,
            highlightsColor = if (highCount > 0) Vector3((hR / highCount).toFloat(), (hG / highCount).toFloat(), (hB / highCount).toFloat()) else Vector3.ZERO
        )
    }
}

object ColorMatchEngine {
    fun match(
        source: Bitmap,
        reference: Bitmap,
        params: ColorMatchParams,
        currentAdjustments: VideoAdjustmentParams = VideoAdjustmentParams.default()
    ): VideoAdjustmentParams {
        // Downscale for analysis performance
        val srcScaled = Bitmap.createScaledBitmap(source, 256, 256, true)
        val refScaled = Bitmap.createScaledBitmap(reference, 256, 256, true)

        val srcAnalysis = ShotMatchAnalyzer.analyze(srcScaled)
        val refAnalysis = ShotMatchAnalyzer.analyze(refScaled)

        var newParams = currentAdjustments

        if (params.matchExposure) {
            val deltaLuma = refAnalysis.averageLuminance - srcAnalysis.averageLuminance
            // Roughly mapping luma delta to exposure stops
            val exposureDiff = deltaLuma * 4.0f
            newParams = newParams.copy(
                exposureStops = currentAdjustments.exposureStops + (exposureDiff * params.matchStrength).coerceIn(-5f, 5f)
            )
        }

        if (params.matchContrast) {
            val srcContrast = max(0.001f, srcAnalysis.contrast)
            val contrastRatio = refAnalysis.contrast / srcContrast
            // Convert ratio to contrast param -1 to +1
            val contrastDiff = (contrastRatio - 1.0f) * 0.5f
            newParams = newParams.copy(
                contrast = currentAdjustments.contrast + (contrastDiff * params.matchStrength).coerceIn(-1f, 1f)
            )
        }

        if (params.matchSaturation) {
            val srcSat = max(0.001f, srcAnalysis.averageSaturation)
            val satRatio = refAnalysis.averageSaturation / srcSat
            val satDiff = satRatio - 1.0f
            // Adjust saturation multiplier
            newParams = newParams.copy(
                saturation = currentAdjustments.saturation * (1f + (satDiff * params.matchStrength)).coerceIn(0f, 2f)
            )
        }

        if (params.matchWhiteBalance) {
            // Very rudimentary WB matching based on average color ratios
            val srcAvg = srcAnalysis.averageColor
            val refAvg = refAnalysis.averageColor
            
            val srcLuma = srcAnalysis.averageLuminance
            val refLuma = refAnalysis.averageLuminance

            // Normalize colors by luma to get tint/temp approx
            if (srcLuma > 0.01f && refLuma > 0.01f) {
                val srcRNorm = srcAvg.x / srcLuma
                val srcBNorm = srcAvg.z / srcLuma
                
                val refRNorm = refAvg.x / refLuma
                val refBNorm = refAvg.z / refLuma
                
                val deltaTemp = (refRNorm - srcRNorm) - (refBNorm - srcBNorm)
                val deltaTint = (refAvg.y / refLuma) - (srcAvg.y / srcLuma)

                // Scale to temp/tint bounds
                newParams = newParams.copy(
                    temperature = currentAdjustments.temperature + (deltaTemp * params.matchStrength * 1.5f).coerceIn(-1f, 1f),
                    tint = currentAdjustments.tint + (deltaTint * params.matchStrength * 1.5f).coerceIn(-1f, 1f)
                )
            }
        }

        return newParams
    }
}
