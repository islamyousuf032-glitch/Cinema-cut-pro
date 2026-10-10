package com.example.model.adjustments

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

@Serializable
data class AdjustmentStack(
    val stackId: String,
    val targetType: TargetType,
    val targetId: String,
    val enabled: Boolean = true,
    val params: VideoAdjustmentParams = VideoAdjustmentParams.default(),
    val keyframes: List<AdjustmentKeyframe> = emptyList(),
    val blendMode: String = "NORMAL",
    val opacity: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {

    fun resetParam(paramName: String): AdjustmentStack {
        // Reflection could be slow, but for data model, we can map common strings or just generate a new copy
        val defaultParams = VideoAdjustmentParams.default()
        val newParams = when (paramName) {
            "exposureStops" -> params.copy(exposureStops = defaultParams.exposureStops)
            "brightness" -> params.copy(brightness = defaultParams.brightness)
            "contrast" -> params.copy(contrast = defaultParams.contrast)
            "highlights" -> params.copy(highlights = defaultParams.highlights)
            "shadows" -> params.copy(shadows = defaultParams.shadows)
            "whites" -> params.copy(whites = defaultParams.whites)
            "blacks" -> params.copy(blacks = defaultParams.blacks)
            "fade" -> params.copy(fade = defaultParams.fade)
            "saturation" -> params.copy(saturation = defaultParams.saturation)
            "vibrance" -> params.copy(vibrance = defaultParams.vibrance)
            "temperature" -> params.copy(temperature = defaultParams.temperature)
            "temperatureKelvin" -> params.copy(temperatureKelvin = defaultParams.temperatureKelvin)
            "tint" -> params.copy(tint = defaultParams.tint)
            "sharpness" -> params.copy(sharpness = defaultParams.sharpness)
            "clarity" -> params.copy(clarity = defaultParams.clarity)
            "structure" -> params.copy(structure = defaultParams.structure)
            "dehaze" -> params.copy(dehaze = defaultParams.dehaze)
            "grainAmount" -> params.copy(grainAmount = defaultParams.grainAmount)
            "grainSize" -> params.copy(grainSize = defaultParams.grainSize)
            "grainRoughness" -> params.copy(grainRoughness = defaultParams.grainRoughness)
            "vignetteAmount" -> params.copy(vignetteAmount = defaultParams.vignetteAmount)
            "vignetteMidpoint" -> params.copy(vignetteMidpoint = defaultParams.vignetteMidpoint)
            "vignetteFeather" -> params.copy(vignetteFeather = defaultParams.vignetteFeather)
            "vignetteRoundness" -> params.copy(vignetteRoundness = defaultParams.vignetteRoundness)
            "shadowColorBalance" -> params.copy(shadowColorBalance = defaultParams.shadowColorBalance)
            "midtoneColorBalance" -> params.copy(midtoneColorBalance = defaultParams.midtoneColorBalance)
            "highlightColorBalance" -> params.copy(highlightColorBalance = defaultParams.highlightColorBalance)
            "redChannelMultiplier" -> params.copy(redChannelMultiplier = defaultParams.redChannelMultiplier)
            "greenChannelMultiplier" -> params.copy(greenChannelMultiplier = defaultParams.greenChannelMultiplier)
            "blueChannelMultiplier" -> params.copy(blueChannelMultiplier = defaultParams.blueChannelMultiplier)
            "redOffset" -> params.copy(redOffset = defaultParams.redOffset)
            "greenOffset" -> params.copy(greenOffset = defaultParams.greenOffset)
            "blueOffset" -> params.copy(blueOffset = defaultParams.blueOffset)
            "rgbChannelMixer" -> params.copy(rgbChannelMixer = defaultParams.rgbChannelMixer)
            else -> params
        }
        
        // Remove keyframes for this param as well when resetting
        val newKeyframes = keyframes.filter { it.parameterId != paramName }
        
        return this.copy(
            params = newParams,
            keyframes = newKeyframes,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun resetAll(): AdjustmentStack {
        return this.copy(
            params = VideoAdjustmentParams.default(),
            keyframes = emptyList(),
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun updateParam(paramId: String, value: Float): AdjustmentStack {
        val newParams = applyParamChange(this.params, paramId, value)
        return this.copy(
            params = AdjustmentValidation.clampAll(newParams),
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun applyPreset(preset: AdjustmentPreset): AdjustmentStack {
        return this.copy(
            params = preset.params, // Should probably apply validation/clamping
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun copyParams(otherStack: AdjustmentStack): AdjustmentStack {
        return this.copy(
            params = otherStack.params,
            keyframes = otherStack.keyframes.toList(),
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun mergeWithAdjustmentLayer(layerStack: AdjustmentStack): VideoAdjustmentParams {
        // Evaluate blend of parameters. For simplified numeric properties, doing an additive or multiplicative blend
        // based on the properties.
        // For right now, returning layerStack params merged additively as an example mechanism
        
        val alpha = layerStack.opacity
        if (alpha == 0f || !layerStack.enabled) return this.params
        
        return VideoAdjustmentParams(
            exposureStops = this.params.exposureStops + (layerStack.params.exposureStops * alpha),
            brightness = this.params.brightness + (layerStack.params.brightness * alpha),
            contrast = this.params.contrast + (layerStack.params.contrast * alpha),
            highlights = this.params.highlights + (layerStack.params.highlights * alpha),
            shadows = this.params.shadows + (layerStack.params.shadows * alpha),
            whites = this.params.whites + (layerStack.params.whites * alpha),
            blacks = this.params.blacks + (layerStack.params.blacks * alpha),
            fade = this.params.fade + (layerStack.params.fade * alpha),
            saturation = this.params.saturation * (1f + ((layerStack.params.saturation - 1f) * alpha)),
            vibrance = this.params.vibrance + (layerStack.params.vibrance * alpha),
            temperature = this.params.temperature + (layerStack.params.temperature * alpha),
            tint = this.params.tint + (layerStack.params.tint * alpha),
            sharpness = this.params.sharpness + (layerStack.params.sharpness * alpha),
            clarity = this.params.clarity + (layerStack.params.clarity * alpha),
            structure = this.params.structure + (layerStack.params.structure * alpha),
            dehaze = this.params.dehaze + (layerStack.params.dehaze * alpha),
            vignetteAmount = this.params.vignetteAmount + (layerStack.params.vignetteAmount * alpha),
            grainAmount = this.params.grainAmount + (layerStack.params.grainAmount * alpha),
            // Multipliers
            redChannelMultiplier = this.params.redChannelMultiplier * (1f + ((layerStack.params.redChannelMultiplier - 1f) * alpha)),
            greenChannelMultiplier = this.params.greenChannelMultiplier * (1f + ((layerStack.params.greenChannelMultiplier - 1f) * alpha)),
            blueChannelMultiplier = this.params.blueChannelMultiplier * (1f + ((layerStack.params.blueChannelMultiplier - 1f) * alpha))
        ).let { AdjustmentValidation.clampAll(it) }
    }

    fun evaluateParamsAtFrame(frame: Long): VideoAdjustmentParams {
        if (keyframes.isEmpty()) return params

        var currentParams = params
        val paramsWithKeyframes = keyframes.groupBy { it.parameterId }

        paramsWithKeyframes.forEach { (paramId, frames) ->
            if (frames.isEmpty()) return@forEach

            val sorted = frames.sortedBy { it.frame }
            val first = sorted.first()
            val last = sorted.last()

            val interpolatedValue = if (frame <= first.frame) {
                first.value
            } else if (frame >= last.frame) {
                last.value
            } else {
                val previous = sorted.last { it.frame <= frame }
                val next = sorted.first { it.frame > frame }
                
                val progress = (frame - previous.frame).toFloat() / (next.frame - previous.frame).toFloat()
                
                // Linear interpolation for now
                when (previous.interpolation) {
                    KeyframeInterpolation.HOLD -> previous.value
                    KeyframeInterpolation.LINEAR -> interpolateLinear(previous.value, next.value, progress)
                    KeyframeInterpolation.EASE_IN -> interpolateLinear(previous.value, next.value, progress.pow(2))
                    KeyframeInterpolation.EASE_OUT -> interpolateLinear(previous.value, next.value, 1f - (1f - progress).pow(2))
                    KeyframeInterpolation.EASE_IN_OUT -> {
                        val easeProgress = if (progress < 0.5f) {
                            2f * progress * progress
                        } else {
                            1f - (-2f * progress + 2f).pow(2) / 2f
                        }
                        interpolateLinear(previous.value, next.value, easeProgress)
                    }
                    KeyframeInterpolation.BEZIER -> interpolateLinear(previous.value, next.value, progress) // Fallback to linear
                }
            }

            // Apply interpolated value
            currentParams = applyParamChange(currentParams, paramId, interpolatedValue)
        }

        return AdjustmentValidation.clampAll(currentParams)
    }
    
    private fun interpolateLinear(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }
    
    private fun applyParamChange(params: VideoAdjustmentParams, paramId: String, value: Float): VideoAdjustmentParams {
        return when (paramId) {
            "exposureStops" -> params.copy(exposureStops = value)
            "brightness" -> params.copy(brightness = value)
            "contrast" -> params.copy(contrast = value)
            "highlights" -> params.copy(highlights = value)
            "shadows" -> params.copy(shadows = value)
            "whites" -> params.copy(whites = value)
            "blacks" -> params.copy(blacks = value)
            "fade" -> params.copy(fade = value)
            "saturation" -> params.copy(saturation = value)
            "vibrance" -> params.copy(vibrance = value)
            "temperature" -> params.copy(temperature = value)
            "temperatureKelvin" -> params.copy(temperatureKelvin = value)
            "tint" -> params.copy(tint = value)
            "sharpness" -> params.copy(sharpness = value)
            "clarity" -> params.copy(clarity = value)
            "structure" -> params.copy(structure = value)
            "dehaze" -> params.copy(dehaze = value)
            "grainAmount" -> params.copy(grainAmount = value)
            "grainSize" -> params.copy(grainSize = value)
            "grainRoughness" -> params.copy(grainRoughness = value)
            "vignetteAmount" -> params.copy(vignetteAmount = value)
            "vignetteMidpoint" -> params.copy(vignetteMidpoint = value)
            "vignetteFeather" -> params.copy(vignetteFeather = value)
            "vignetteRoundness" -> params.copy(vignetteRoundness = value)
            "redChannelMultiplier" -> params.copy(redChannelMultiplier = value)
            "greenChannelMultiplier" -> params.copy(greenChannelMultiplier = value)
            "blueChannelMultiplier" -> params.copy(blueChannelMultiplier = value)
            "redOffset" -> params.copy(redOffset = value)
            "greenOffset" -> params.copy(greenOffset = value)
            "blueOffset" -> params.copy(blueOffset = value)
            "shadowBalanceR" -> params.copy(shadowColorBalance = params.shadowColorBalance.copy(x = value))
            "shadowBalanceG" -> params.copy(shadowColorBalance = params.shadowColorBalance.copy(y = value))
            "shadowBalanceB" -> params.copy(shadowColorBalance = params.shadowColorBalance.copy(z = value))
            "midtoneBalanceR" -> params.copy(midtoneColorBalance = params.midtoneColorBalance.copy(x = value))
            "midtoneBalanceG" -> params.copy(midtoneColorBalance = params.midtoneColorBalance.copy(y = value))
            "midtoneBalanceB" -> params.copy(midtoneColorBalance = params.midtoneColorBalance.copy(z = value))
            "highlightBalanceR" -> params.copy(highlightColorBalance = params.highlightColorBalance.copy(x = value))
            "highlightBalanceG" -> params.copy(highlightColorBalance = params.highlightColorBalance.copy(y = value))
            "highlightBalanceB" -> params.copy(highlightColorBalance = params.highlightColorBalance.copy(z = value))
            "liftR" -> params.copy(lift = params.lift.copy(x = value))
            "liftG" -> params.copy(lift = params.lift.copy(y = value))
            "liftB" -> params.copy(lift = params.lift.copy(z = value))
            "gammaR" -> params.copy(gamma = params.gamma.copy(x = value))
            "gammaG" -> params.copy(gamma = params.gamma.copy(y = value))
            "gammaB" -> params.copy(gamma = params.gamma.copy(z = value))
            "gainR" -> params.copy(gain = params.gain.copy(x = value))
            "gainG" -> params.copy(gain = params.gain.copy(y = value))
            "gainB" -> params.copy(gain = params.gain.copy(z = value))
            "offsetR" -> params.copy(offsetWheel = params.offsetWheel.copy(x = value))
            "offsetG" -> params.copy(offsetWheel = params.offsetWheel.copy(y = value))
            "offsetB" -> params.copy(offsetWheel = params.offsetWheel.copy(z = value))
            "skinToneProtection" -> params.copy(skinToneProtection = value)
            "skinToneShowMask" -> params.copy(skinToneShowMask = value > 0.5f)
            "skinToneHueCenter" -> params.copy(skinToneHueCenter = value)
            "skinToneHueWidth" -> params.copy(skinToneHueWidth = value)
            "skinToneReset" -> params.copy(
                skinToneProtection = 0f,
                skinToneShowMask = false,
                skinToneHueCenter = 0.083f,
                skinToneHueWidth = 0.05f
            )
            "lutIntensity" -> params.copy(lutIntensity = value)
            else -> params
        }
    }
}
