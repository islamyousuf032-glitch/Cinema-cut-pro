package com.example.model.adjustments.engine

import kotlin.math.*

object AdjustmentMath {
    fun clamp(value: Float, min: Float = 0f, max: Float = 1f): Float {
        return value.coerceIn(min, max)
    }

    fun mix(x: Float, y: Float, a: Float): Float {
        return x * (1f - a) + y * a
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun luminance(r: Float, g: Float, b: Float): Float {
        // Rec. 709 luma coefficients
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun rgb2hsl(r: Float, g: Float, b: Float, hslOut: FloatArray) {
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        var h = 0f
        var s = 0f
        val l = (max + min) / 2f

        if (max != min) {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            h = when (max) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                b -> (r - g) / d + 4f
                else -> 0f
            }
            h /= 6f
        }
        hslOut[0] = h
        hslOut[1] = s
        hslOut[2] = l
    }

    fun hsl2rgb(h: Float, s: Float, l: Float, rgbOut: FloatArray) {
        if (s == 0f) {
            rgbOut[0] = l
            rgbOut[1] = l
            rgbOut[2] = l
        } else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            rgbOut[0] = hue2rgb(p, q, h + 1f/3f)
            rgbOut[1] = hue2rgb(p, q, h)
            rgbOut[2] = hue2rgb(p, q, h - 1f/3f)
        }
    }

    private fun hue2rgb(p: Float, q: Float, tInput: Float): Float {
        var t = tInput
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        if (t < 1f/6f) return p + (q - p) * 6f * t
        if (t < 1f/2f) return q
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f
        return p
    }
}
