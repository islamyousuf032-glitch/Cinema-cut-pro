package com.example.model.colorgrade.engine

import android.graphics.Bitmap
import com.example.model.colorgrade.ColorGradeStack
import com.example.model.colorgrade.ColorGradeParams
import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint
import com.example.model.colorgrade.HslQualifierParams
import com.example.model.colorgrade.SelectiveColorParams
import com.example.model.colorgrade.SkinToneProtectionParams

object CurveProcessor {
    fun process(rgb: FloatArray, params: CurveParams) {
        // Master
        rgb[0] = evaluateCurve(rgb[0], params.masterPoints)
        rgb[1] = evaluateCurve(rgb[1], params.masterPoints)
        rgb[2] = evaluateCurve(rgb[2], params.masterPoints)

        // RGB
        rgb[0] = evaluateCurve(rgb[0], params.redPoints)
        rgb[1] = evaluateCurve(rgb[1], params.greenPoints)
        rgb[2] = evaluateCurve(rgb[2], params.bluePoints)

        // Luma
        val lumaOrig = ColorMath.luminance(rgb[0], rgb[1], rgb[2])
        val lumaNew = evaluateCurve(lumaOrig, params.lumaPoints)
        if (lumaOrig > 0.001f) {
            val ratio = lumaNew / lumaOrig
            rgb[0] *= ratio
            rgb[1] *= ratio
            rgb[2] *= ratio
        }

        rgb[0] = ColorMath.clamp(rgb[0])
        rgb[1] = ColorMath.clamp(rgb[1])
        rgb[2] = ColorMath.clamp(rgb[2])

        val hsl = FloatArray(3)
        ColorMath.rgb2hsl(rgb[0], rgb[1], rgb[2], hsl)
        
        // Hue vs Hue - 0.5 is no change
        val hueShift = (evaluateCurve(hsl[0], params.hueVsHuePoints) - 0.5f) * 2f
        hsl[0] = (hsl[0] + hueShift) % 1f
        if (hsl[0] < 0f) hsl[0] += 1f
        
        // Hue vs Sat - 0.5 is no change
        hsl[1] += (evaluateCurve(hsl[0], params.hueVsSatPoints) - 0.5f) * 2f
        
        // Hue vs Luma
        hsl[2] += (evaluateCurve(hsl[0], params.hueVsLumaPoints) - 0.5f) * 2f

        // Sat vs Sat
        hsl[1] += (evaluateCurve(hsl[1], params.satVsSatPoints) - 0.5f) * 2f

        // Luma vs Sat 
        hsl[1] += (evaluateCurve(hsl[2], params.lumaVsSatPoints) - 0.5f) * 2f
        
        hsl[1] = ColorMath.clamp(hsl[1])
        hsl[2] = ColorMath.clamp(hsl[2])
        
        ColorMath.hsl2rgb(hsl[0], hsl[1], hsl[2], rgb)
    }

    private fun evaluateCurve(x: Float, points: List<CurvePoint>): Float {
        if (points.isEmpty()) return x
        if (points.size == 1) return points[0].y

        var p0 = points.first()
        if (x <= p0.x) return p0.y

        for (i in 1 until points.size) {
            val p1 = points[i]
            if (x <= p1.x) {
                // Linear interpolation
                val t = (x - p0.x) / maxOf(0.0001f, (p1.x - p0.x))
                return ColorMath.mix(p0.y, p1.y, t)
            }
            p0 = p1
        }
        return points.last().y
    }
}

object HslQualifierProcessor {
    fun getMask(rgb: FloatArray, params: HslQualifierParams): Float {
        if (!params.enabled) return 1f
        val hsl = FloatArray(3)
        ColorMath.rgb2hsl(rgb[0], rgb[1], rgb[2], hsl)
        
        // hue distance
        var hd = kotlin.math.abs(hsl[0] - params.hueCenter)
        if (hd > 0.5f) hd = 1f - hd
        val hMask = 1f - ColorMath.smoothstep(params.hueWidth, params.hueWidth + params.hueFeather, hd * 2f)
        
        // sat
        val sMask = ColorMath.smoothstep(params.saturationMin - params.saturationFeather, params.saturationMin, hsl[1]) * 
                    (1f - ColorMath.smoothstep(params.saturationMax, params.saturationMax + params.saturationFeather, hsl[1]))
                    
        // luma
        val lMask = ColorMath.smoothstep(params.luminanceMin - params.luminanceFeather, params.luminanceMin, hsl[2]) * 
                    (1f - ColorMath.smoothstep(params.luminanceMax, params.luminanceMax + params.luminanceFeather, hsl[2]))
        
        var mask = hMask * sMask * lMask
        if (params.invertMask) mask = 1f - mask
        return mask
    }
}

object HslAdjustmentProcessor {
    fun process(rgb: FloatArray, mask: Float, params: HslQualifierParams) {
        val hsl = FloatArray(3)
        ColorMath.rgb2hsl(rgb[0], rgb[1], rgb[2], hsl)

        // Apply hue/sat/luma directly in HSL space
        hsl[0] = (hsl[0] + params.hueShift * mask) % 1f
        if (hsl[0] < 0f) hsl[0] += 1f
        
        // Saturation multiplier
        hsl[1] += (params.saturation - 1f) * hsl[1] * mask
        
        // Luminance additive
        hsl[2] += params.luminance * mask
        
        hsl[1] = ColorMath.clamp(hsl[1])
        hsl[2] = ColorMath.clamp(hsl[2])
        ColorMath.hsl2rgb(hsl[0], hsl[1], hsl[2], rgb)

        // Then apply temperature/tint, contrast in RGB, scaled by mask
        if (mask > 0f) {
            val tc = ColorGradeParams(
                temperature = params.temperature * mask,
                tint = params.tint * mask,
                contrast = params.contrast * mask
            )
            PrimaryColorCorrector.process(rgb, tc)
        }
    }
}

object SelectiveColorProcessor {
    fun process(rgb: FloatArray, params: SelectiveColorParams) {
        val hsl = FloatArray(3)
        ColorMath.rgb2hsl(rgb[0], rgb[1], rgb[2], hsl)
        
        // Simplistic selective color (binning hues)
        // Red = ~0.0, Yellow = ~0.16, Green = ~0.33, Cyan = ~0.5, Blue = ~0.66, Magenta = ~0.83
        val h = hsl[0]
        
        // Helper to apply range
        fun applyRange(hCenter: Float, range: com.example.model.colorgrade.SelectiveColorRange) {
            var hd = kotlin.math.abs(h - hCenter)
            if (hd > 0.5f) hd = 1f - hd
            val mask = 1f - ColorMath.smoothstep(0f, range.softness + 0.1f, hd)
            if (mask > 0f) {
                hsl[0] = (hsl[0] + range.hueShift * mask) % 1f
                if (hsl[0] < 0f) hsl[0] += 1f
                hsl[1] += range.saturation * mask
                hsl[2] += range.luminance * mask
            }
        }
        
        applyRange(0.0f, params.red)
        applyRange(0.08f, params.orange)
        applyRange(0.16f, params.yellow)
        applyRange(0.33f, params.green)
        applyRange(0.5f, params.cyan)
        applyRange(0.66f, params.blue)
        applyRange(0.75f, params.purple)
        applyRange(0.83f, params.magenta)
        
        hsl[1] = ColorMath.clamp(hsl[1])
        hsl[2] = ColorMath.clamp(hsl[2])
        ColorMath.hsl2rgb(hsl[0], hsl[1], hsl[2], rgb)
    }
}

object SkinToneProtectionProcessor {
    fun process(rgbBefore: FloatArray, rgbAfter: FloatArray, params: SkinToneProtectionParams) {
        if (!params.enabled || params.strength == 0f) return
        
        val hslBefore = FloatArray(3)
        ColorMath.rgb2hsl(rgbBefore[0], rgbBefore[1], rgbBefore[2], hslBefore)
        
        // Skin tone hue is generally around 0.05 to 0.1
        var hd = kotlin.math.abs(hslBefore[0] - 0.08f)
        if (hd > 0.5f) hd = 1f - hd
        
        val hMask = 1f - ColorMath.smoothstep(0f, params.hueRange, hd)
        val sMask = ColorMath.smoothstep(0.1f, 0.1f + params.saturationRange, hslBefore[1]) * 
                    (1f - ColorMath.smoothstep(0.8f - params.saturationRange, 0.9f, hslBefore[1]))
        val lMask = ColorMath.smoothstep(0.1f, 0.1f + params.luminanceRange, hslBefore[2]) * 
                    (1f - ColorMath.smoothstep(0.8f - params.luminanceRange, 0.9f, hslBefore[2]))
        
        val mask = hMask * sMask * lMask * params.strength
        
        // Blend back based on mask
        rgbAfter[0] = ColorMath.mix(rgbAfter[0], rgbBefore[0], mask)
        rgbAfter[1] = ColorMath.mix(rgbAfter[1], rgbBefore[1], mask)
        rgbAfter[2] = ColorMath.mix(rgbAfter[2], rgbBefore[2], mask)
    }
}
