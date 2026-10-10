package com.example.model.adjustments.engine

import com.example.model.adjustments.VideoAdjustmentParams
import kotlin.math.*
import kotlin.random.Random

import android.graphics.Bitmap

class CpuFrameProcessor {
    private val pixelTemp = FloatArray(3)
    private val outColorTemp = FloatArray(3)

    companion object {
        fun process(bitmap: Bitmap, params: VideoAdjustmentParams): Bitmap {
            val rawPixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(rawPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            // Exposure
            if (params.exposureStops != 0f) {
                com.example.model.colorgrade.engine.NativeColorGradeEngine.nativeApplyExposure(rawPixels, bitmap.width, bitmap.height, params.exposureStops)
            }
            
            // Contrast
            if (params.contrast != 0f) {
                com.example.model.colorgrade.engine.NativeColorGradeEngine.nativeApplyContrast(rawPixels, bitmap.width, bitmap.height, params.contrast)
            }
            
            // Saturation
            if (params.saturation != 1f) {
                com.example.model.colorgrade.engine.NativeColorGradeEngine.nativeApplySaturation(rawPixels, bitmap.width, bitmap.height, params.saturation)
            }
            
            // Temperature/Tint
            if (params.temperature != 0f || params.tint != 0f) {
                com.example.model.colorgrade.engine.NativeColorGradeEngine.nativeApplyTemperatureTint(rawPixels, bitmap.width, bitmap.height, params.temperature, params.tint)
            }
            
            // Lift/Gamma/Gain
            val liftR = params.shadowColorBalance.x; val liftG = params.shadowColorBalance.y; val liftB = params.shadowColorBalance.z
            val gammaR = params.midtoneColorBalance.x; val gammaG = params.midtoneColorBalance.y; val gammaB = params.midtoneColorBalance.z
            val gainR = params.highlightColorBalance.x; val gainG = params.highlightColorBalance.y; val gainB = params.highlightColorBalance.z
            
            val hasLift = liftR != 0f || liftG != 0f || liftB != 0f
            val hasGamma = gammaR != 0f || gammaG != 0f || gammaB != 0f
            val hasGain = gainR != 0f || gainG != 0f || gainB != 0f
            
            if (hasLift || hasGamma || hasGain) {
                val lr = liftR * 0.5f; val lg = liftG * 0.5f; val lb = liftB * 0.5f
                val gmr = 1f - gammaR * 0.5f; val gmg = 1f - gammaG * 0.5f; val gmb = 1f - gammaB * 0.5f
                val gr = 1f + gainR * 0.5f; val gg = 1f + gainG * 0.5f; val gb = 1f + gainB * 0.5f
                com.example.model.colorgrade.engine.NativeColorGradeEngine.nativeApplyLiftGammaGain(rawPixels, bitmap.width, bitmap.height, lr, lg, lb, gmr, gmg, gmb, gr, gg, gb)
            }
            
            return Bitmap.createBitmap(rawPixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        }
    }

    fun processFrame(
        inputFrame: AdjustmentPreviewFrame,
        params: VideoAdjustmentParams,
        colorEngine: ColorTransformEngine,
        frameTime: Long = 0L
    ): AdjustmentPreviewFrame {
        val width = inputFrame.width
        val height = inputFrame.height
        val inPixels = inputFrame.pixels
        val outPixels = FloatArray(inPixels.size)
        
        val random = Random(frameTime)
        val pivot = 0.5f 

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = (y * width + x) * 4
                val inR = inPixels[idx]
                val inG = inPixels[idx + 1]
                val inB = inPixels[idx + 2]
                val alpha = inPixels[idx + 3]
                
                // Map to linear
                var r = inR
                var g = inG
                var b = inB
                
                // Color Management Input
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
                        } else {
                            // SRGB/Rec709 to Linear fallback
                            r = if (r <= 0.04045f) r / 12.92f else Math.pow(((r + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
                            g = if (g <= 0.04045f) g / 12.92f else Math.pow(((g + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
                            b = if (b <= 0.04045f) b / 12.92f else Math.pow(((b + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
                        }
                    }
                }
                
                val tempR = if (params.temperature > 0) 1f + params.temperature * 0.2f else 1f
                val tempB = if (params.temperature < 0) 1f - params.temperature * 0.2f else 1f
                val tintG = if (params.tint > 0) 1f + params.tint * 0.2f else 1f
                val tintR = if (params.tint < 0) 1f - params.tint * 0.2f else 1f
                
                r *= tempR * tintR
                g *= tintG
                b *= tempB
                
                val exposureMult = 2f.pow(params.exposureStops)
                r *= exposureMult
                g *= exposureMult
                b *= exposureMult
                
                r += params.brightness * 0.2f
                g += params.brightness * 0.2f
                b += params.brightness * 0.2f
                
                if (params.contrast != 0f) {
                    val factor = max(0f, 1f + params.contrast)
                    r = pivot + (r - pivot) * factor
                    g = pivot + (g - pivot) * factor
                    b = pivot + (b - pivot) * factor
                }
                
                var luma = AdjustmentMath.luminance(r, g, b)
                
                val shadowMask = 1f - AdjustmentMath.smoothstep(0f, 0.4f, luma)
                val highlightMask = AdjustmentMath.smoothstep(0.6f, 1f, luma)
                
                r += (params.shadows * shadowMask * 0.5f) - (params.highlights * highlightMask * 0.5f)
                g += (params.shadows * shadowMask * 0.5f) - (params.highlights * highlightMask * 0.5f)
                b += (params.shadows * shadowMask * 0.5f) - (params.highlights * highlightMask * 0.5f)
                
                val blackMask = 1f - AdjustmentMath.smoothstep(0f, 0.1f, luma)
                val whiteMask = AdjustmentMath.smoothstep(0.9f, 1f, luma)
                
                r += (params.blacks * blackMask * 0.2f) - (params.whites * whiteMask * 0.2f)
                g += (params.blacks * blackMask * 0.2f) - (params.whites * whiteMask * 0.2f)
                b += (params.blacks * blackMask * 0.2f) - (params.whites * whiteMask * 0.2f)
                
                if (params.fade > 0f) {
                    val fadeAmount = params.fade * 0.2f
                    r = AdjustmentMath.mix(r, fadeAmount + r * (1f - fadeAmount * 2f), params.fade)
                    g = AdjustmentMath.mix(g, fadeAmount + g * (1f - fadeAmount * 2f), params.fade)
                    b = AdjustmentMath.mix(b, fadeAmount + b * (1f - fadeAmount * 2f), params.fade)
                }
                
                // DaVinci-inspired Professional Color Wheels
                // Standard CDL / Resolve math:
                // Offset is added first
                r += params.offsetWheel.x
                g += params.offsetWheel.y
                b += params.offsetWheel.z
                
                // Lift / Gamma / Gain
                // Lift applies more to shadows (1 - in), Gain applies to highlights
                r = max(0f, r * params.gain.x + params.lift.x * (1f - r))
                g = max(0f, g * params.gain.y + params.lift.y * (1f - g))
                b = max(0f, b * params.gain.z + params.lift.z * (1f - b))
                
                // Gamma (power)
                r = max(r, 0f).pow(1f / max(params.gamma.x, 0.001f))
                g = max(g, 0f).pow(1f / max(params.gamma.y, 0.001f))
                b = max(b, 0f).pow(1f / max(params.gamma.z, 0.001f))

                // Skin Tone Protection
                val hslTemp = FloatArray(3)
                
                luma = AdjustmentMath.luminance(r, g, b)
                
                if (params.saturation != 1f) {
                    r = AdjustmentMath.mix(luma, r, params.saturation)
                    g = AdjustmentMath.mix(luma, g, params.saturation)
                    b = AdjustmentMath.mix(luma, b, params.saturation)
                }
                
                if (params.vibrance != 0f) {
                    val sat = max(r, max(g, b)) - min(r, min(g, b))
                    val vibMult = 1f + params.vibrance * (1f - sat)
                    r = AdjustmentMath.mix(luma, r, max(0f, vibMult))
                    g = AdjustmentMath.mix(luma, g, max(0f, vibMult))
                    b = AdjustmentMath.mix(luma, b, max(0f, vibMult))
                }

                // HSL Adjustment
                if (params.hslHue != null || params.hslSat != null || params.hslLum != null) {
                    AdjustmentMath.rgb2hsl(r, g, b, hslTemp)
                    val bin = (hslTemp[0] + 0.0625f) % 1f * 8f
                    val idx = bin.toInt() % 8
                    val idxNext = (idx + 1) % 8
                    val f = bin % 1f
                    
                    val hh = params.hslHue
                    if (hh != null) {
                        hslTemp[0] = (hslTemp[0] + hh[idx] * (1f - f) + hh[idxNext] * f) % 1f
                        if (hslTemp[0] < 0f) hslTemp[0] += 1f
                    }
                    val hs = params.hslSat
                    if (hs != null) {
                        hslTemp[1] = AdjustmentMath.clamp(hslTemp[1] + hs[idx] * (1f - f) + hs[idxNext] * f)
                    }
                    val hl = params.hslLum
                    if (hl != null) {
                        hslTemp[2] = AdjustmentMath.clamp(hslTemp[2] + hl[idx] * (1f - f) + hl[idxNext] * f)
                    }
                    AdjustmentMath.hsl2rgb(hslTemp[0], hslTemp[1], hslTemp[2], hslTemp)
                    r = hslTemp[0]; g = hslTemp[1]; b = hslTemp[2]
                }
                
                val sm = AdjustmentMath.smoothstep(params.shadowLumaRange[1], params.shadowLumaRange[0], luma)
                val hm = AdjustmentMath.smoothstep(params.highlightLumaRange[0], params.highlightLumaRange[1], luma)
                val mm = max(0f, 1f - sm - hm)
                
                r += params.shadowColorBalance.x * sm + params.midtoneColorBalance.x * mm + params.highlightColorBalance.x * hm
                g += params.shadowColorBalance.y * sm + params.midtoneColorBalance.y * mm + params.highlightColorBalance.y * hm
                b += params.shadowColorBalance.z * sm + params.midtoneColorBalance.z * mm + params.highlightColorBalance.z * hm
                
                r = r * params.redChannelMultiplier + params.redOffset
                g = g * params.greenChannelMultiplier + params.greenOffset
                b = b * params.blueChannelMultiplier + params.blueOffset
                
                val mx = params.rgbChannelMixer
                if (mx != null && mx.size >= 9) {
                    val mixR = r * mx[0] + g * mx[1] + b * mx[2]
                    val mixG = r * mx[3] + g * mx[4] + b * mx[5]
                    val mixB = r * mx[6] + g * mx[7] + b * mx[8]
                    r = mixR; g = mixG; b = mixB
                }
                
                if (params.dehaze != 0f) {
                    val dark = min(r, min(g, b))
                    val dehazeAmount = params.dehaze * 0.5f
                    r -= dark * dehazeAmount
                    g -= dark * dehazeAmount
                    b -= dark * dehazeAmount
                }
                
                if (params.vignetteAmount != 0f) {
                    var cx = (x - width / 2f) / (width / 2f)
                    val cy = (y - height / 2f) / (height / 2f)
                    if (params.vignetteRoundness != 0f) {
                        cx *= 1f - params.vignetteRoundness * 0.5f
                    }
                    val dist = sqrt(cx * cx + cy * cy)
                    val vfactor = AdjustmentMath.smoothstep(params.vignetteMidpoint, params.vignetteMidpoint + params.vignetteFeather + 0.001f, dist)
                    val vamount = params.vignetteAmount * vfactor
                    r *= (1f - vamount)
                    g *= (1f - vamount)
                    b *= (1f - vamount)
                }
                
                if (params.grainAmount > 0f) {
                    // Extremely simplistic grain modeling
                    val noise = random.nextFloat() - 0.5f
                    val grain = noise * params.grainAmount * 0.2f * (0.5f + params.grainRoughness * 0.5f)
                    r += grain
                    g += grain
                    b += grain
                }

                if (params.skinToneProtection > 0f || params.skinToneShowMask) {
                    AdjustmentMath.rgb2hsl(inR, inG, inB, hslTemp)
                    
                    var hueDist = abs(hslTemp[0] - params.skinToneHueCenter)
                    if (hueDist > 0.5f) { hueDist = 1f - hueDist }
                    
                    var skinMask = 1f - AdjustmentMath.smoothstep(params.skinToneHueWidth * 0.5f, params.skinToneHueWidth, hueDist)
                    skinMask *= AdjustmentMath.smoothstep(0.15f, 0.25f, hslTemp[1]) * (1f - AdjustmentMath.smoothstep(0.85f, 0.95f, hslTemp[1]))
                    skinMask *= AdjustmentMath.smoothstep(0.15f, 0.25f, hslTemp[2]) * (1f - AdjustmentMath.smoothstep(0.85f, 0.95f, hslTemp[2]))
                    
                    if (params.skinToneShowMask) {
                        r = skinMask
                        g = 0f
                        b = 0f
                    } else if (params.skinToneProtection > 0f) {
                        val protectAmount = skinMask * params.skinToneProtection
                        r = r * (1f - protectAmount) + inR * protectAmount
                        g = g * (1f - protectAmount) + inG * protectAmount
                        b = b * (1f - protectAmount) + inB * protectAmount
                    }
                }

                // Tone Mapping and Output Transform

                if (params.outputColorSpace == com.example.model.adjustments.ColorSpaceProfile.REC_709 || params.outputColorSpace == com.example.model.adjustments.ColorSpaceProfile.DCI_P3) {
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
                    // Linear to SRGB
                    outColorTemp[0] = if (r <= 0.0031308f) 12.92f * r else 1.055f * Math.pow(r.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
                    outColorTemp[1] = if (g <= 0.0031308f) 12.92f * g else 1.055f * Math.pow(g.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
                    outColorTemp[2] = if (b <= 0.0031308f) 12.92f * b else 1.055f * Math.pow(b.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
                } else if (params.outputColorSpace == com.example.model.adjustments.ColorSpaceProfile.HLG) {
                    outColorTemp[0] = Math.pow(r.coerceIn(0f, 1f).toDouble(), 0.5).toFloat()
                    outColorTemp[1] = Math.pow(g.coerceIn(0f, 1f).toDouble(), 0.5).toFloat()
                    outColorTemp[2] = Math.pow(b.coerceIn(0f, 1f).toDouble(), 0.5).toFloat()
                } else {
                    outColorTemp[0] = r
                    outColorTemp[1] = g
                    outColorTemp[2] = b
                }
                
                outColorTemp[0] = AdjustmentMath.clamp(outColorTemp[0])
                outColorTemp[1] = AdjustmentMath.clamp(outColorTemp[1])
                outColorTemp[2] = AdjustmentMath.clamp(outColorTemp[2])
                
                outPixels[idx] = outColorTemp[0]
                outPixels[idx + 1] = outColorTemp[1]
                outPixels[idx + 2] = outColorTemp[2]
                outPixels[idx + 3] = AdjustmentMath.clamp(alpha)
            }
        }
        
        if (params.sharpness > 0f || params.clarity != 0f || params.structure != 0f) {
            applyLocalContrast(outPixels, width, height, params)
        }
        
        return AdjustmentPreviewFrame(width, height, outPixels)
    }

    private fun applyLocalContrast(pixels: FloatArray, width: Int, height: Int, params: VideoAdjustmentParams) {
        val amount = params.sharpness * 2f + params.clarity * 0.5f + params.structure * 0.5f
        if (amount == 0f || width < 3 || height < 3) return
        
        val temp = FloatArray(pixels.size)
        System.arraycopy(pixels, 0, temp, 0, pixels.size)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = (y * width + x) * 4
                
                var sumR = 0f; var sumG = 0f; var sumB = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nIdx = ((y + dy) * width + (x + dx)) * 4
                        sumR += temp[nIdx]
                        sumG += temp[nIdx + 1]
                        sumB += temp[nIdx + 2]
                    }
                }
                
                val blurR = sumR / 9f
                val blurG = sumG / 9f
                val blurB = sumB / 9f
                
                pixels[idx] = AdjustmentMath.clamp(pixels[idx] + (pixels[idx] - blurR) * amount)
                pixels[idx + 1] = AdjustmentMath.clamp(pixels[idx + 1] + (pixels[idx + 1] - blurG) * amount)
                pixels[idx + 2] = AdjustmentMath.clamp(pixels[idx + 2] + (pixels[idx + 2] - blurB) * amount)
            }
        }
    }
}
