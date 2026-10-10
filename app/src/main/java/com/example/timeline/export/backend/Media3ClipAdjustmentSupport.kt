package com.example.timeline.export.backend

import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.VideoAdjustmentParams

/**
 * Defines the deliberately small set of clip adjustments rendered by the Media3 export adapter.
 * Unsupported values are rejected during validation; they are never silently dropped.
 */
internal object Media3ClipAdjustmentSupport {
    private val defaults = VideoAdjustmentParams.default()

    fun hasRenderableAdjustments(stack: AdjustmentStack): Boolean =
        stack.enabled && hasRenderableAdjustments(stack.params)

    fun hasRenderableAdjustments(params: VideoAdjustmentParams): Boolean =
        params.exposureStops != defaults.exposureStops ||
            params.brightness != defaults.brightness ||
            params.contrast != defaults.contrast ||
            params.saturation != defaults.saturation ||
            params.vibrance != defaults.vibrance ||
            params.temperature != defaults.temperature ||
            params.tint != defaults.tint ||
            params.sharpness != defaults.sharpness ||
            params.clarity != defaults.clarity ||
            params.structure != defaults.structure

    /** Native parameter order: exposure, brightness, contrast, saturation, vibrance, temperature,
     * tint, then the combined sharpness/clarity/structure amount. */
    fun nativeParameters(params: VideoAdjustmentParams): FloatArray = floatArrayOf(
        params.exposureStops,
        params.brightness,
        params.contrast,
        params.saturation,
        params.vibrance,
        params.temperature,
        params.tint,
        params.sharpness * 2f + params.clarity * 0.5f + params.structure * 0.5f
    )

    fun validationErrors(
        stack: AdjustmentStack,
        nativeEngineAvailable: Boolean
    ): List<String> {
        if (!stack.enabled) return emptyList()

        val errors = mutableListOf<String>()
        if (stack.keyframes.isNotEmpty()) {
            errors += "Animated clip adjustments are not supported by this export path yet."
        }
        if (stack.blendMode != "NORMAL" || stack.opacity != 1f) {
            errors += "Clip-adjustment blend modes and opacity are not supported by this export path yet."
        }

        val unsupported = unsupportedAdjustmentNames(stack.params)
        if (unsupported.isNotEmpty()) {
            errors += "This export path does not support these clip adjustments yet: ${unsupported.joinToString()}."
        }

        val invalidValues = invalidSupportedAdjustmentNames(stack.params)
        if (invalidValues.isNotEmpty()) {
            errors += "Clip-adjustment values are outside their supported ranges: ${invalidValues.joinToString()}."
        }

        if (hasRenderableAdjustments(stack) && !nativeEngineAvailable) {
            errors += "The native color-adjustment engine is unavailable on this device; clip adjustments cannot be exported."
        }

        return errors
    }

    private fun invalidSupportedAdjustmentNames(params: VideoAdjustmentParams): List<String> = buildList {
        addIfOutOfRange("exposure", params.exposureStops, -5f, 5f)
        addIfOutOfRange("brightness", params.brightness, -1f, 1f)
        addIfOutOfRange("contrast", params.contrast, -1f, 1f)
        addIfOutOfRange("saturation", params.saturation, 0f, 2f)
        addIfOutOfRange("vibrance", params.vibrance, -1f, 1f)
        addIfOutOfRange("temperature", params.temperature, -1f, 1f)
        addIfOutOfRange("tint", params.tint, -1f, 1f)
        addIfOutOfRange("sharpness", params.sharpness, 0f, 1f)
        addIfOutOfRange("mid detail", params.clarity, -1f, 1f)
        addIfOutOfRange("structure", params.structure, -1f, 1f)
    }

    private fun MutableList<String>.addIfOutOfRange(
        name: String,
        value: Float,
        minimum: Float,
        maximum: Float
    ) {
        if (!value.isFinite() || value < minimum || value > maximum) add(name)
    }

    private fun unsupportedAdjustmentNames(params: VideoAdjustmentParams): List<String> = buildList {
        if (params.highlights != defaults.highlights) add("highlights")
        if (params.shadows != defaults.shadows) add("shadows")
        if (params.whites != defaults.whites) add("whites")
        if (params.blacks != defaults.blacks) add("blacks")
        if (params.fade != defaults.fade) add("fade")
        if (params.temperatureKelvin != defaults.temperatureKelvin) add("Kelvin temperature")
        if (params.dehaze != defaults.dehaze) add("dehaze")

        if (params.grainAmount != defaults.grainAmount ||
            params.grainSize != defaults.grainSize ||
            params.grainRoughness != defaults.grainRoughness
        ) add("film grain")
        if (params.vignetteAmount != defaults.vignetteAmount ||
            params.vignetteMidpoint != defaults.vignetteMidpoint ||
            params.vignetteFeather != defaults.vignetteFeather ||
            params.vignetteRoundness != defaults.vignetteRoundness
        ) add("vignette")

        if (params.lift != defaults.lift || params.gamma != defaults.gamma || params.gain != defaults.gain ||
            params.offsetWheel != defaults.offsetWheel
        ) add("color wheels")
        if (params.skinToneProtection != defaults.skinToneProtection ||
            params.skinToneShowMask != defaults.skinToneShowMask ||
            params.skinToneHueCenter != defaults.skinToneHueCenter ||
            params.skinToneHueWidth != defaults.skinToneHueWidth
        ) add("skin-tone protection")
        if (params.shadowColorBalance != defaults.shadowColorBalance ||
            params.midtoneColorBalance != defaults.midtoneColorBalance ||
            params.highlightColorBalance != defaults.highlightColorBalance ||
            !params.shadowLumaRange.contentEquals(defaults.shadowLumaRange) ||
            !params.midtoneLumaRange.contentEquals(defaults.midtoneLumaRange) ||
            !params.highlightLumaRange.contentEquals(defaults.highlightLumaRange)
        ) add("color balance")

        if (params.inputLogType != defaults.inputLogType || params.inputLogProfile != defaults.inputLogProfile ||
            params.inputColorSpace != defaults.inputColorSpace || params.outputColorSpace != defaults.outputColorSpace ||
            params.toneMappingMode != defaults.toneMappingMode || params.highlightRolloff != defaults.highlightRolloff
        ) add("log/color-space transforms")

        if (params.hslHue != defaults.hslHue || params.hslSat != defaults.hslSat || params.hslLum != defaults.hslLum) {
            add("selective HSL bands")
        }
        if (params.lutId != defaults.lutId || params.lutIntensity != defaults.lutIntensity) add("LUT")
        if (params.qualEnabled != defaults.qualEnabled || params.qualHueCenter != defaults.qualHueCenter ||
            params.qualHueWidth != defaults.qualHueWidth || params.qualHueFeather != defaults.qualHueFeather ||
            params.qualSatMin != defaults.qualSatMin || params.qualSatMax != defaults.qualSatMax ||
            params.qualSatFeather != defaults.qualSatFeather || params.qualLumMin != defaults.qualLumMin ||
            params.qualLumMax != defaults.qualLumMax || params.qualLumFeather != defaults.qualLumFeather ||
            params.qualInvert != defaults.qualInvert || params.qualShowMatte != defaults.qualShowMatte ||
            params.qualHueShift != defaults.qualHueShift || params.qualSat != defaults.qualSat ||
            params.qualLum != defaults.qualLum || params.qualContrast != defaults.qualContrast ||
            params.qualTemp != defaults.qualTemp || params.qualTint != defaults.qualTint
        ) add("HSL qualifier")

        if (params.redChannelMultiplier != defaults.redChannelMultiplier ||
            params.greenChannelMultiplier != defaults.greenChannelMultiplier ||
            params.blueChannelMultiplier != defaults.blueChannelMultiplier ||
            params.redOffset != defaults.redOffset || params.greenOffset != defaults.greenOffset ||
            params.blueOffset != defaults.blueOffset || params.rgbChannelMixer != defaults.rgbChannelMixer
        ) add("RGB channel mixer")
    }
}
