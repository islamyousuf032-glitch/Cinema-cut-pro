package com.example.model.adjustments.engine

import com.example.model.adjustments.ColorPipelineSettings
import kotlin.math.pow

class ColorTransformEngine(private val settings: ColorPipelineSettings) {
    
    fun toWorkingSpace(r: Float, g: Float, b: Float, out: FloatArray) {
        // Convert SRGB to Linear as identity fallback
        out[0] = if (r <= 0.04045f) r / 12.92f else ((r + 0.055f) / 1.055f).pow(2.4f)
        out[1] = if (g <= 0.04045f) g / 12.92f else ((g + 0.055f) / 1.055f).pow(2.4f)
        out[2] = if (b <= 0.04045f) b / 12.92f else ((b + 0.055f) / 1.055f).pow(2.4f)
    }

    fun toOutputSpace(r: Float, g: Float, b: Float, out: FloatArray) {
        var mappedR = r
        var mappedG = g
        var mappedB = b

        if (settings.clampMode == "LUMINANCE") {
           val l = AdjustmentMath.luminance(mappedR, mappedG, mappedB)
           if (l > 1f) {
               val f = 1f / l
               mappedR *= f
               mappedG *= f
               mappedB *= f
           }
        } else if (settings.clampMode == "RGB") {
           mappedR = AdjustmentMath.clamp(mappedR)
           mappedG = AdjustmentMath.clamp(mappedG)
           mappedB = AdjustmentMath.clamp(mappedB)
        }

        // Linear to SRGB
        out[0] = if (mappedR <= 0.0031308f) 12.92f * mappedR else 1.055f * mappedR.pow(1f / 2.4f) - 0.055f
        out[1] = if (mappedG <= 0.0031308f) 12.92f * mappedG else 1.055f * mappedG.pow(1f / 2.4f) - 0.055f
        out[2] = if (mappedB <= 0.0031308f) 12.92f * mappedB else 1.055f * mappedB.pow(1f / 2.4f) - 0.055f
        
        out[0] = AdjustmentMath.clamp(out[0])
        out[1] = AdjustmentMath.clamp(out[1])
        out[2] = AdjustmentMath.clamp(out[2])
    }
}
