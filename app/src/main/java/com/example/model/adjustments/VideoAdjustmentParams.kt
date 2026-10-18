package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
data class Vector3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
    }
}

@Serializable
data class VideoAdjustmentParams(
    // Light
    val exposureStops: Float = 0f, // -5.0 to +5.0
    val brightness: Float = 0f, // -1.0 to +1.0
    val contrast: Float = 0f, // -1.0 to +1.0
    val highlights: Float = 0f, // -1.0 to +1.0
    val shadows: Float = 0f, // -1.0 to +1.0
    val whites: Float = 0f, // -1.0 to +1.0
    val blacks: Float = 0f, // -1.0 to +1.0
    val fade: Float = 0f, // 0.0 to 1.0

    // Color
    val saturation: Float = 1f, // 0.0 to 2.0
    val vibrance: Float = 0f, // -1.0 to +1.0
    val temperature: Float = 0f, // -1.0 to +1.0
    val temperatureKelvin: Float? = null, // 2000K to 50000K
    val tint: Float = 0f, // -1.0 to +1.0

    // Detail
    val sharpness: Float = 0f, // 0.0 to 1.0
    val clarity: Float = 0f, // -1.0 to +1.0
    val structure: Float = 0f, // -1.0 to +1.0
    val dehaze: Float = 0f, // -1.0 to +1.0

    // Effects
    val grainAmount: Float = 0f, // 0.0 to 1.0
    val grainSize: Float = 0.5f, // 0.0 to 1.0
    val grainRoughness: Float = 0.5f, // 0.0 to 1.0
    val vignetteAmount: Float = 0f, // -1.0 to +1.0
    val vignetteMidpoint: Float = 0.5f, // 0.0 to 1.0
    val vignetteFeather: Float = 0.5f, // 0.0 to 1.0
    val vignetteRoundness: Float = 0f, // -1.0 to +1.0

    // Professional Color Wheels (ASC CDL + Lift)
    val lift: Vector3 = Vector3.ZERO,
    val gamma: Vector3 = Vector3.ONE,
    val gain: Vector3 = Vector3.ONE,
    val offsetWheel: Vector3 = Vector3.ZERO,
    
    // Skin Tone Protection
    val skinToneProtection: Float = 0f, // 0.0 to 1.0
    val skinToneShowMask: Boolean = false,
    val skinToneHueCenter: Float = 0.083f,
    val skinToneHueWidth: Float = 0.05f,

    // Color Balance
    val shadowColorBalance: Vector3 = Vector3.ZERO,
    val midtoneColorBalance: Vector3 = Vector3.ZERO,
    val highlightColorBalance: Vector3 = Vector3.ZERO,
    val shadowLumaRange: FloatArray = floatArrayOf(0f, 0.33f),
    val midtoneLumaRange: FloatArray = floatArrayOf(0.33f, 0.66f),
    val highlightLumaRange: FloatArray = floatArrayOf(0.66f, 1.0f),

    // Log to Rec.709 Conversion
    val inputLogType: String = "None", 

    // HSL Adjustment Bands (Hue, Sat, Lum for Primary Colors)
    // Indexes: 0=Red, 1=Orange, 2=Yellow, 3=Green, 4=Cyan, 5=Blue, 6=Purple, 7=Magenta
    val hslHue: FloatArray? = null, 
    val hslSat: FloatArray? = null,
    val hslLum: FloatArray? = null,

    // LUT
    val lutId: String? = null,
    val lutIntensity: Float = 1f,

    // Color Management
    val inputLogProfile: com.example.model.adjustments.LogProfile = com.example.model.adjustments.LogProfile.NONE,
    val inputColorSpace: com.example.model.adjustments.ColorSpaceProfile = com.example.model.adjustments.ColorSpaceProfile.REC_709,
    val outputColorSpace: com.example.model.adjustments.ColorSpaceProfile = com.example.model.adjustments.ColorSpaceProfile.REC_709,
    val toneMappingMode: com.example.model.adjustments.engine.ToneMappingMode = com.example.model.adjustments.engine.ToneMappingMode.NONE,
    val highlightRolloff: Float = 0f,

    // HSL Qualifier
    val qualEnabled: Boolean = false,
    val qualHueCenter: Float = 0.5f,
    val qualHueWidth: Float = 0.1f,
    val qualHueFeather: Float = 0.05f,
    val qualSatMin: Float = 0f,
    val qualSatMax: Float = 1f,
    val qualSatFeather: Float = 0.1f,
    val qualLumMin: Float = 0f,
    val qualLumMax: Float = 1f,
    val qualLumFeather: Float = 0.1f,
    val qualInvert: Boolean = false,
    val qualShowMatte: Boolean = false,
    
    val qualHueShift: Float = 0f,
    val qualSat: Float = 1f,
    val qualLum: Float = 0f,
    val qualContrast: Float = 0f,
    val qualTemp: Float = 0f,
    val qualTint: Float = 0f,

    // RGB Channel
    val redChannelMultiplier: Float = 1f,
    val greenChannelMultiplier: Float = 1f,
    val blueChannelMultiplier: Float = 1f,
    val redOffset: Float = 0f,
    val greenOffset: Float = 0f,
    val blueOffset: Float = 0f,
    val rgbChannelMixer: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VideoAdjustmentParams

        if (exposureStops != other.exposureStops) return false
        if (brightness != other.brightness) return false
        if (contrast != other.contrast) return false
        if (highlights != other.highlights) return false
        if (shadows != other.shadows) return false
        if (whites != other.whites) return false
        if (blacks != other.blacks) return false
        if (fade != other.fade) return false
        if (saturation != other.saturation) return false
        if (vibrance != other.vibrance) return false
        if (temperature != other.temperature) return false
        if (temperatureKelvin != other.temperatureKelvin) return false
        if (tint != other.tint) return false
        if (sharpness != other.sharpness) return false
        if (clarity != other.clarity) return false
        if (structure != other.structure) return false
        if (dehaze != other.dehaze) return false
        if (grainAmount != other.grainAmount) return false
        if (grainSize != other.grainSize) return false
        if (grainRoughness != other.grainRoughness) return false
        if (vignetteAmount != other.vignetteAmount) return false
        if (vignetteMidpoint != other.vignetteMidpoint) return false
        if (vignetteFeather != other.vignetteFeather) return false
        if (vignetteRoundness != other.vignetteRoundness) return false
        if (lift != other.lift) return false
        if (gamma != other.gamma) return false
        if (gain != other.gain) return false
        if (offsetWheel != other.offsetWheel) return false
        if (skinToneProtection != other.skinToneProtection) return false
        if (skinToneShowMask != other.skinToneShowMask) return false
        if (skinToneHueCenter != other.skinToneHueCenter) return false
        if (skinToneHueWidth != other.skinToneHueWidth) return false
        if (shadowColorBalance != other.shadowColorBalance) return false
        if (midtoneColorBalance != other.midtoneColorBalance) return false
        if (highlightColorBalance != other.highlightColorBalance) return false
        if (!shadowLumaRange.contentEquals(other.shadowLumaRange)) return false
        if (!midtoneLumaRange.contentEquals(other.midtoneLumaRange)) return false
        if (!highlightLumaRange.contentEquals(other.highlightLumaRange)) return false
        if (inputLogType != other.inputLogType) return false
        if (lutId != other.lutId) return false
        if (lutIntensity != other.lutIntensity) return false
        if (inputLogProfile != other.inputLogProfile) return false
        if (inputColorSpace != other.inputColorSpace) return false
        if (outputColorSpace != other.outputColorSpace) return false
        if (toneMappingMode != other.toneMappingMode) return false
        if (highlightRolloff != other.highlightRolloff) return false
        
        if (hslHue != null) {
            if (other.hslHue == null) return false
            if (!hslHue.contentEquals(other.hslHue)) return false
        } else if (other.hslHue != null) return false

        if (hslSat != null) {
            if (other.hslSat == null) return false
            if (!hslSat.contentEquals(other.hslSat)) return false
        } else if (other.hslSat != null) return false

        if (hslLum != null) {
            if (other.hslLum == null) return false
            if (!hslLum.contentEquals(other.hslLum)) return false
        } else if (other.hslLum != null) return false

        if (redChannelMultiplier != other.redChannelMultiplier) return false
        if (greenChannelMultiplier != other.greenChannelMultiplier) return false
        if (blueChannelMultiplier != other.blueChannelMultiplier) return false
        if (redOffset != other.redOffset) return false
        if (greenOffset != other.greenOffset) return false
        if (blueOffset != other.blueOffset) return false
        if (rgbChannelMixer != null) {
            if (other.rgbChannelMixer == null) return false
            if (!rgbChannelMixer.contentEquals(other.rgbChannelMixer)) return false
        } else if (other.rgbChannelMixer != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = exposureStops.hashCode()
        result = 31 * result + brightness.hashCode()
        result = 31 * result + contrast.hashCode()
        result = 31 * result + highlights.hashCode()
        result = 31 * result + shadows.hashCode()
        result = 31 * result + whites.hashCode()
        result = 31 * result + blacks.hashCode()
        result = 31 * result + fade.hashCode()
        result = 31 * result + saturation.hashCode()
        result = 31 * result + vibrance.hashCode()
        result = 31 * result + temperature.hashCode()
        result = 31 * result + (temperatureKelvin?.hashCode() ?: 0)
        result = 31 * result + tint.hashCode()
        result = 31 * result + sharpness.hashCode()
        result = 31 * result + clarity.hashCode()
        result = 31 * result + structure.hashCode()
        result = 31 * result + dehaze.hashCode()
        result = 31 * result + grainAmount.hashCode()
        result = 31 * result + grainSize.hashCode()
        result = 31 * result + grainRoughness.hashCode()
        result = 31 * result + vignetteAmount.hashCode()
        result = 31 * result + vignetteMidpoint.hashCode()
        result = 31 * result + vignetteFeather.hashCode()
        result = 31 * result + vignetteRoundness.hashCode()
        result = 31 * result + lift.hashCode()
        result = 31 * result + gamma.hashCode()
        result = 31 * result + gain.hashCode()
        result = 31 * result + offsetWheel.hashCode()
        result = 31 * result + skinToneProtection.hashCode()
        result = 31 * result + skinToneShowMask.hashCode()
        result = 31 * result + skinToneHueCenter.hashCode()
        result = 31 * result + skinToneHueWidth.hashCode()
        result = 31 * result + shadowColorBalance.hashCode()
        result = 31 * result + midtoneColorBalance.hashCode()
        result = 31 * result + highlightColorBalance.hashCode()
        result = 31 * result + shadowLumaRange.contentHashCode()
        result = 31 * result + midtoneLumaRange.contentHashCode()
        result = 31 * result + highlightLumaRange.contentHashCode()
        result = 31 * result + inputLogType.hashCode()
        result = 31 * result + (lutId?.hashCode() ?: 0)
        result = 31 * result + lutIntensity.hashCode()
        result = 31 * result + inputLogProfile.hashCode()
        result = 31 * result + inputColorSpace.hashCode()
        result = 31 * result + outputColorSpace.hashCode()
        result = 31 * result + toneMappingMode.hashCode()
        result = 31 * result + highlightRolloff.hashCode()
        result = 31 * result + (hslHue?.contentHashCode() ?: 0)
        result = 31 * result + (hslSat?.contentHashCode() ?: 0)
        result = 31 * result + (hslLum?.contentHashCode() ?: 0)
        result = 31 * result + redChannelMultiplier.hashCode()
        result = 31 * result + greenChannelMultiplier.hashCode()
        result = 31 * result + blueChannelMultiplier.hashCode()
        result = 31 * result + redOffset.hashCode()
        result = 31 * result + greenOffset.hashCode()
        result = 31 * result + blueOffset.hashCode()
        result = 31 * result + (rgbChannelMixer?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        fun default(): VideoAdjustmentParams = VideoAdjustmentParams()
    }
}
