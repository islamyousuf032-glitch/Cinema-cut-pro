package com.example.model.colorgrade.engine

import kotlin.math.*

object ColorMath {
    fun clamp(value: Float, min: Float = 0f, max: Float = 1f): Float = value.coerceIn(min, max)
    
    fun luminance(r: Float, g: Float, b: Float): Float {
        // Rec. 709 luma coefficients
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun mix(x: Float, y: Float, a: Float): Float = x * (1f - a) + y * a

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
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

    fun rgb2hsv(r: Float, g: Float, b: Float, hsvOut: FloatArray) {
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val d = max - min
        
        var h = 0f
        val s = if (max == 0f) 0f else d / max
        val v = max

        if (max != min) {
            h = when (max) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                b -> (r - g) / d + 4f
                else -> 0f
            }
            h /= 6f
        }
        
        hsvOut[0] = h
        hsvOut[1] = s
        hsvOut[2] = v
    }

    fun hsv2rgb(h: Float, s: Float, v: Float, rgbOut: FloatArray) {
        if (s == 0f) {
            rgbOut[0] = v; rgbOut[1] = v; rgbOut[2] = v
            return
        }
        
        val hScaled = (if (h >= 1f) 0f else h) * 6f
        val i = hScaled.toInt()
        val f = hScaled - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        
        when (i) {
            0 -> { rgbOut[0] = v; rgbOut[1] = t; rgbOut[2] = p }
            1 -> { rgbOut[0] = q; rgbOut[1] = v; rgbOut[2] = p }
            2 -> { rgbOut[0] = p; rgbOut[1] = v; rgbOut[2] = t }
            3 -> { rgbOut[0] = p; rgbOut[1] = q; rgbOut[2] = v }
            4 -> { rgbOut[0] = t; rgbOut[1] = p; rgbOut[2] = v }
            else -> { rgbOut[0] = v; rgbOut[1] = p; rgbOut[2] = q }
        }
    }
}
