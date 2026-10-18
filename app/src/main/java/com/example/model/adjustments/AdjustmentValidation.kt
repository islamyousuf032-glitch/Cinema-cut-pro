package com.example.model.adjustments

object AdjustmentValidation {

    fun clampExposureParams(value: Float, param: String): Float {
        return when (param) {
            "exposureStops" -> value.coerceIn(-5.0f, +5.0f)
            "brightness", "contrast", "highlights", "shadows", "whites", "blacks" -> value.coerceIn(-1.0f, +1.0f)
            "fade" -> value.coerceIn(0.0f, 1.0f)
            else -> value
        }
    }

    fun clampColorParams(value: Float, param: String): Float {
        return when (param) {
            "saturation" -> value.coerceIn(0.0f, 2.0f)
            "vibrance", "temperature", "tint" -> value.coerceIn(-1.0f, +1.0f)
            "temperatureKelvin" -> value.coerceIn(2000f, 50000f)
            else -> value
        }
    }

    fun clampDetailParams(value: Float, param: String): Float {
        return when (param) {
            "sharpness" -> value.coerceIn(0.0f, 1.0f)
            "clarity", "structure", "dehaze" -> value.coerceIn(-1.0f, +1.0f)
            else -> value
        }
    }

    fun clampEffectsParams(value: Float, param: String): Float {
        return when (param) {
            "grainAmount", "grainSize", "grainRoughness", "vignetteMidpoint", "vignetteFeather" -> value.coerceIn(0.0f, 1.0f)
            "vignetteAmount", "vignetteRoundness" -> value.coerceIn(-1.0f, +1.0f)
            else -> value
        }
    }

    fun clampAll(params: VideoAdjustmentParams): VideoAdjustmentParams {
        return params.copy(
            exposureStops = clampExposureParams(params.exposureStops, "exposureStops"),
            brightness = clampExposureParams(params.brightness, "brightness"),
            contrast = clampExposureParams(params.contrast, "contrast"),
            highlights = clampExposureParams(params.highlights, "highlights"),
            shadows = clampExposureParams(params.shadows, "shadows"),
            whites = clampExposureParams(params.whites, "whites"),
            blacks = clampExposureParams(params.blacks, "blacks"),
            fade = clampExposureParams(params.fade, "fade"),
            
            saturation = clampColorParams(params.saturation, "saturation"),
            vibrance = clampColorParams(params.vibrance, "vibrance"),
            temperature = clampColorParams(params.temperature, "temperature"),
            temperatureKelvin = params.temperatureKelvin?.let { clampColorParams(it, "temperatureKelvin") },
            tint = clampColorParams(params.tint, "tint"),
            
            sharpness = clampDetailParams(params.sharpness, "sharpness"),
            clarity = clampDetailParams(params.clarity, "clarity"),
            structure = clampDetailParams(params.structure, "structure"),
            dehaze = clampDetailParams(params.dehaze, "dehaze"),
            
            grainAmount = clampEffectsParams(params.grainAmount, "grainAmount"),
            grainSize = clampEffectsParams(params.grainSize, "grainSize"),
            grainRoughness = clampEffectsParams(params.grainRoughness, "grainRoughness"),
            vignetteAmount = clampEffectsParams(params.vignetteAmount, "vignetteAmount"),
            vignetteMidpoint = clampEffectsParams(params.vignetteMidpoint, "vignetteMidpoint"),
            vignetteFeather = clampEffectsParams(params.vignetteFeather, "vignetteFeather"),
            vignetteRoundness = clampEffectsParams(params.vignetteRoundness, "vignetteRoundness"),
            
            // Multiplier & Balance constraints
            redChannelMultiplier = params.redChannelMultiplier.coerceIn(0f, 2f),
            greenChannelMultiplier = params.greenChannelMultiplier.coerceIn(0f, 2f),
            blueChannelMultiplier = params.blueChannelMultiplier.coerceIn(0f, 2f),
            redOffset = params.redOffset.coerceIn(-1f, 1f),
            greenOffset = params.greenOffset.coerceIn(-1f, 1f),
            blueOffset = params.blueOffset.coerceIn(-1f, 1f),
            
            shadowColorBalance = Vector3(
                params.shadowColorBalance.x.coerceIn(-1f, 1f),
                params.shadowColorBalance.y.coerceIn(-1f, 1f),
                params.shadowColorBalance.z.coerceIn(-1f, 1f)
            ),
            midtoneColorBalance = Vector3(
                params.midtoneColorBalance.x.coerceIn(-1f, 1f),
                params.midtoneColorBalance.y.coerceIn(-1f, 1f),
                params.midtoneColorBalance.z.coerceIn(-1f, 1f)
            ),
            highlightColorBalance = Vector3(
                params.highlightColorBalance.x.coerceIn(-1f, 1f),
                params.highlightColorBalance.y.coerceIn(-1f, 1f),
                params.highlightColorBalance.z.coerceIn(-1f, 1f)
            )
        )
    }
}
