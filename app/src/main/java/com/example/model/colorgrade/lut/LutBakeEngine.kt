package com.example.model.colorgrade.lut

import com.example.model.adjustments.VideoAdjustmentParams
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LutBakeEngine {

    suspend fun bakeGradeToCube(
        params: VideoAdjustmentParams, 
        size: Int = 33, 
        destFile: File, 
        title: String = "Exported LUT"
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            FileOutputStream(destFile).use { fos ->
                bakeGradeToCubeStream(params, size, fos, title)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun bakeGradeToCubeStream(
        params: VideoAdjustmentParams,
        size: Int,
        outputStream: OutputStream,
        title: String
    ) {
        PrintWriter(outputStream).use { writer ->
            writer.println("TITLE \"\$title\"")
            writer.println("LUT_3D_SIZE \$size")
            writer.println("DOMAIN_MIN 0.0 0.0 0.0")
            writer.println("DOMAIN_MAX 1.0 1.0 1.0")
            writer.println()

            val engine = CpuLutBakeProcessor(params)

            val step = 1.0f / (size - 1)
            
            for (bIdx in 0 until size) {
                val b = bIdx * step
                for (gIdx in 0 until size) {
                    val g = gIdx * step
                    for (rIdx in 0 until size) {
                        val r = rIdx * step
                        
                        val outColor = engine.processColor(floatArrayOf(r, g, b))
                        writer.println("\${outColor[0]} \${outColor[1]} \${outColor[2]}")
                    }
                }
            }
        }
    }
}

// Minimal CPU processor since exporting complex effect via CPU is very advanced.
// We map the main colors for generating the LUT.
class CpuLutBakeProcessor(private val params: VideoAdjustmentParams) {
    
    fun processColor(rgbIn: FloatArray): FloatArray {
        var r = rgbIn[0]
        var g = rgbIn[1]
        var b = rgbIn[2]
        
        // Input Log Transform (SLog3, CLog, VLog -> Linear appx)
        when (params.inputLogProfile) {
            com.example.model.adjustments.LogProfile.SLOG3 -> {
                fun dec(c: Float) = if (c >= 0.011f) Math.pow(10.0, (c - 0.420) / 0.2615).toFloat() - 0.01f else (c - 0.0929f) / 5.05f
                r = dec(r); g = dec(g); b = dec(b)
            }
            com.example.model.adjustments.LogProfile.CLOG, com.example.model.adjustments.LogProfile.CLOG2, com.example.model.adjustments.LogProfile.CLOG3 -> {
                fun dec(c: Float) = Math.pow(c.toDouble(), 2.2).toFloat() * 1.2f - 0.1f
                r = dec(r); g = dec(g); b = dec(b)
            }
            com.example.model.adjustments.LogProfile.VLOG -> {
                fun dec(c: Float) = Math.pow(c.toDouble(), 2.2).toFloat() * 1.1f
                r = dec(r); g = dec(g); b = dec(b)
            }
            else -> {
                if (params.inputColorSpace == com.example.model.adjustments.ColorSpaceProfile.REC_2020) {
                    r = Math.pow(r.toDouble(), 2.4).toFloat()
                    g = Math.pow(g.toDouble(), 2.4).toFloat()
                    b = Math.pow(b.toDouble(), 2.4).toFloat()
                }
            }
        }

        // Very basic exposure & brightness
        val exposureGain = Math.pow(2.0, params.exposureStops.toDouble()).toFloat()
        r *= exposureGain
        g *= exposureGain
        b *= exposureGain
        
        r += params.brightness
        g += params.brightness
        b += params.brightness
        
        // Basic Contrast
        val contrastFactor = (1.0f + params.contrast) / (1.0f - params.contrast).coerceAtLeast(0.01f)
        r = ((r - 0.5f) * contrastFactor) + 0.5f
        g = ((g - 0.5f) * contrastFactor) + 0.5f
        b = ((b - 0.5f) * contrastFactor) + 0.5f
        
        // Tone Mapping and Output Transform
        if (params.outputColorSpace == com.example.model.adjustments.ColorSpaceProfile.REC_709) {
            when (params.toneMappingMode) {
                com.example.model.adjustments.engine.ToneMappingMode.SIMPLE -> {
                    r /= (1.0f + r)
                    g /= (1.0f + g)
                    b /= (1.0f + b)
                }
                com.example.model.adjustments.engine.ToneMappingMode.FILMIC -> {
                    fun filmic(c: Float): Float {
                        val x = Math.max(0.0f, c - 0.004f)
                        return (x * (6.2f * x + 0.5f)) / (x * (6.2f * x + 1.7f) + 0.06f)
                    }
                    r = filmic(r); g = filmic(g); b = filmic(b)
                }
                com.example.model.adjustments.engine.ToneMappingMode.HIGHLIGHT_ROLLOFF -> {
                    val thresh = 0.9f * (1f - params.highlightRolloff) + 0.5f * params.highlightRolloff
                    fun roll(c: Float) = if (c > thresh) thresh + (1f - thresh) * (1f - Math.exp((-(c - thresh) / (1f - thresh)).toDouble()).toFloat()) else c
                    r = roll(r); g = roll(g); b = roll(b)
                }
                else -> {
                    // None -> Hard clip
                    r = r.coerceIn(0f, 1f); g = g.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)
                }
            }
            // Gamma 2.4/2.2 convert
            r = Math.pow(r.coerceIn(0f, 1f).toDouble(), 1.0 / 2.2).toFloat()
            g = Math.pow(g.coerceIn(0f, 1f).toDouble(), 1.0 / 2.2).toFloat()
            b = Math.pow(b.coerceIn(0f, 1f).toDouble(), 1.0 / 2.2).toFloat()
        } else if (params.outputColorSpace == com.example.model.adjustments.ColorSpaceProfile.HLG) {
            r = Math.pow(r.coerceIn(0f, 1f).toDouble(), 0.5).toFloat()
            g = Math.pow(g.coerceIn(0f, 1f).toDouble(), 0.5).toFloat()
            b = Math.pow(b.coerceIn(0f, 1f).toDouble(), 0.5).toFloat()
        }

        return floatArrayOf(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }
}
