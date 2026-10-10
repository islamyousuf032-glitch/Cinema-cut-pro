package com.example.model.colorgrade.engine

import com.example.model.colorgrade.ColorGradeParams
import kotlin.math.max
import kotlin.math.pow

object PrimaryColorCorrector {

    fun process(rgb: FloatArray, params: ColorGradeParams) {
        var r = rgb[0]
        var g = rgb[1]
        var b = rgb[2]

        // Lift
        r = r * (1f - params.lift.r) + params.lift.r
        g = g * (1f - params.lift.g) + params.lift.g
        b = b * (1f - params.lift.b) + params.lift.b

        // Gamma (avoid div by 0 and neg numbers for pow)
        r = max(r, 0f).pow(1f / max(params.gamma.r + 1f, 0.001f))
        g = max(g, 0f).pow(1f / max(params.gamma.g + 1f, 0.001f))
        b = max(b, 0f).pow(1f / max(params.gamma.b + 1f, 0.001f))

        // Gain
        r *= (params.gain.r + 1f)
        g *= (params.gain.g + 1f)
        b *= (params.gain.b + 1f)

        // Offset
        r += params.offset.r
        g += params.offset.g
        b += params.offset.b
        
        // Temperature (yellow/blue shift)
        val temp = params.temperature
        if (temp > 0f) {
            r += temp * 0.2f
            b -= temp * 0.2f
        } else {
            r += temp * 0.2f
            b -= temp * 0.2f
        }
        
        // Tint (green/magenta shift)
        val tint = params.tint
        g += tint * 0.2f

        // Contrast and Pivot
        if (params.contrast != 0f) {
            val c = if (params.contrast > 0f) {
                1f / (1f - params.contrast)
            } else {
                1f + params.contrast
            }
            val p = params.pivot
            r = (r - p) * c + p
            g = (g - p) * c + p
            b = (b - p) * c + p
        }

        // Saturation && Vibrance
        var luma = ColorMath.luminance(r, g, b)
        if (params.saturation != 1f) {
            r = ColorMath.mix(luma, r, params.saturation)
            g = ColorMath.mix(luma, g, params.saturation)
            b = ColorMath.mix(luma, b, params.saturation)
        }
        
        if (params.vibrance != 0f) {
            val maxColor = max(r, max(g, b))
            val minColor = kotlin.math.min(r, kotlin.math.min(g, b))
            val satAmount = maxColor - minColor
            val vibMult = params.vibrance * (1f - satAmount)
            r = ColorMath.mix(luma, r, 1f + vibMult)
            g = ColorMath.mix(luma, g, 1f + vibMult)
            b = ColorMath.mix(luma, b, 1f + vibMult)
        }
        
        // Ranges: Shadows, Midtones, Highlights
        luma = ColorMath.luminance(r, g, b)
        
        if (params.shadows.r != 0f || params.shadows.g != 0f || params.shadows.b != 0f) {
            val sm = ColorMath.smoothstep(params.shadows.lumaMax, params.shadows.lumaMin, luma)
            r += params.shadows.r * sm
            g += params.shadows.g * sm
            b += params.shadows.b * sm
        }
        
        if (params.midtones.r != 0f || params.midtones.g != 0f || params.midtones.b != 0f) {
            // Approximation distance from midtone center
            val center = (params.midtones.lumaMin + params.midtones.lumaMax) / 2f
            val width = (params.midtones.lumaMax - params.midtones.lumaMin) / 2f
            val mm = 1f - ColorMath.smoothstep(0f, width, kotlin.math.abs(luma - center))
            r += params.midtones.r * mm
            g += params.midtones.g * mm
            b += params.midtones.b * mm
        }

        if (params.highlights.r != 0f || params.highlights.g != 0f || params.highlights.b != 0f) {
            val hm = ColorMath.smoothstep(params.highlights.lumaMin, params.highlights.lumaMax, luma)
            r += params.highlights.r * hm
            g += params.highlights.g * hm
            b += params.highlights.b * hm
        }

        rgb[0] = r
        rgb[1] = g
        rgb[2] = b
    }
}
