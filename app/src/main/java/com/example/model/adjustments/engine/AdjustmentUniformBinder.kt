package com.example.model.adjustments.engine

import androidx.media3.common.util.GlProgram
import com.example.model.adjustments.VideoAdjustmentParams
import kotlin.math.pow

object AdjustmentUniformBinder {
    private fun GlProgram.safeFloat(name: String, value: Float) { try { setFloatUniform(name, value) } catch (e: Exception) {} }
    private fun GlProgram.safeInt(name: String, value: Int) { try { setIntUniform(name, value) } catch (e: Exception) {} }

    fun bindUniforms(glProgram: GlProgram, params: VideoAdjustmentParams, presentationTimeUs: Long) {
        // Input Color Management
        val logTypeInt = when (params.inputLogProfile) {
            com.example.model.adjustments.LogProfile.SLOG3 -> 1
            com.example.model.adjustments.LogProfile.CLOG, com.example.model.adjustments.LogProfile.CLOG2, com.example.model.adjustments.LogProfile.CLOG3 -> 2
            com.example.model.adjustments.LogProfile.VLOG -> 3
            else -> if (params.inputColorSpace == com.example.model.adjustments.ColorSpaceProfile.REC_2020) 4 else 0
        }
        glProgram.safeInt("uInputLogType", logTypeInt)
        
        val outputTargetInt = when (params.outputColorSpace) {
            com.example.model.adjustments.ColorSpaceProfile.REC_2020, com.example.model.adjustments.ColorSpaceProfile.REC_2020_LINEAR -> 1
            com.example.model.adjustments.ColorSpaceProfile.HLG, com.example.model.adjustments.ColorSpaceProfile.PQ -> 2
            else -> 0
        }
        glProgram.safeInt("uOutputColorSpace", outputTargetInt)
        
        val toneMapInt = when (params.toneMappingMode) {
            com.example.model.adjustments.engine.ToneMappingMode.SIMPLE -> 1
            com.example.model.adjustments.engine.ToneMappingMode.FILMIC -> 2
            com.example.model.adjustments.engine.ToneMappingMode.HIGHLIGHT_ROLLOFF -> 3
            else -> 0
        }
        glProgram.safeInt("uToneMappingMode", toneMapInt)
        glProgram.safeFloat("uHighlightRolloff", params.highlightRolloff)

        // Light
        glProgram.safeFloat("uExposure", 2f.pow(params.exposureStops))
        glProgram.safeFloat("uBrightness", params.brightness)
        glProgram.safeFloat("uContrast", params.contrast)
        glProgram.safeFloat("uHighlights", params.highlights)
        glProgram.safeFloat("uShadows", params.shadows)
        glProgram.safeFloat("uWhites", params.whites)
        glProgram.safeFloat("uBlacks", params.blacks)
        glProgram.safeFloat("uFade", params.fade)
        
        // Professional Color Wheels
        glProgram.safeFloat("uLiftR", params.lift.x)
        glProgram.safeFloat("uLiftG", params.lift.y)
        glProgram.safeFloat("uLiftB", params.lift.z)
        glProgram.safeFloat("uGammaR", params.gamma.x)
        glProgram.safeFloat("uGammaG", params.gamma.y)
        glProgram.safeFloat("uGammaB", params.gamma.z)
        glProgram.safeFloat("uGainR", params.gain.x)
        glProgram.safeFloat("uGainG", params.gain.y)
        glProgram.safeFloat("uGainB", params.gain.z)
        glProgram.safeFloat("uOffsetWheelR", params.offsetWheel.x)
        glProgram.safeFloat("uOffsetWheelG", params.offsetWheel.y)
        glProgram.safeFloat("uOffsetWheelB", params.offsetWheel.z)
        
        // Skin Tone Protection
        glProgram.safeFloat("uSkinToneProtection", params.skinToneProtection)
        glProgram.safeInt("uSkinToneShowMask", if (params.skinToneShowMask) 1 else 0)
        glProgram.safeFloat("uSkinToneHueCenter", params.skinToneHueCenter)
        glProgram.safeFloat("uSkinToneHueWidth", params.skinToneHueWidth)
        
        // HSL
        val hue = params.hslHue ?: floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        glProgram.safeFloat("uHslHue0", hue[0])
        glProgram.safeFloat("uHslHue1", hue[1])
        glProgram.safeFloat("uHslHue2", hue[2])
        glProgram.safeFloat("uHslHue3", hue[3])
        glProgram.safeFloat("uHslHue4", hue[4])
        glProgram.safeFloat("uHslHue5", hue[5])
        glProgram.safeFloat("uHslHue6", hue[6])
        glProgram.safeFloat("uHslHue7", hue[7])

        val sat = params.hslSat ?: floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        glProgram.safeFloat("uHslSat0", sat[0])
        glProgram.safeFloat("uHslSat1", sat[1])
        glProgram.safeFloat("uHslSat2", sat[2])
        glProgram.safeFloat("uHslSat3", sat[3])
        glProgram.safeFloat("uHslSat4", sat[4])
        glProgram.safeFloat("uHslSat5", sat[5])
        glProgram.safeFloat("uHslSat6", sat[6])
        glProgram.safeFloat("uHslSat7", sat[7])

        val lum = params.hslLum ?: floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        glProgram.safeFloat("uHslLum0", lum[0])
        glProgram.safeFloat("uHslLum1", lum[1])
        glProgram.safeFloat("uHslLum2", lum[2])
        glProgram.safeFloat("uHslLum3", lum[3])
        glProgram.safeFloat("uHslLum4", lum[4])
        glProgram.safeFloat("uHslLum5", lum[5])
        glProgram.safeFloat("uHslLum6", lum[6])
        glProgram.safeFloat("uHslLum7", lum[7])

        // HSL Qualifier
        glProgram.safeInt("uQualEnabled", if (params.qualEnabled) 1 else 0)
        glProgram.safeFloat("uQualHueCenter", params.qualHueCenter)
        glProgram.safeFloat("uQualHueWidth", params.qualHueWidth)
        glProgram.safeFloat("uQualHueFeather", params.qualHueFeather)
        glProgram.safeFloat("uQualSatMin", params.qualSatMin)
        glProgram.safeFloat("uQualSatMax", params.qualSatMax)
        glProgram.safeFloat("uQualSatFeather", params.qualSatFeather)
        glProgram.safeFloat("uQualLumMin", params.qualLumMin)
        glProgram.safeFloat("uQualLumMax", params.qualLumMax)
        glProgram.safeFloat("uQualLumFeather", params.qualLumFeather)
        glProgram.safeInt("uQualInvert", if (params.qualInvert) 1 else 0)
        glProgram.safeInt("uQualShowMatte", if (params.qualShowMatte) 1 else 0)
        
        glProgram.safeFloat("uQualHueShift", params.qualHueShift)
        glProgram.safeFloat("uQualSat", params.qualSat)
        glProgram.safeFloat("uQualLum", params.qualLum)
        glProgram.safeFloat("uQualContrast", params.qualContrast)
        glProgram.safeFloat("uQualTemp", params.qualTemp)
        glProgram.safeFloat("uQualTint", params.qualTint)

        // Color
        glProgram.safeFloat("uSaturation", params.saturation)
        glProgram.safeFloat("uVibrance", params.vibrance)
        glProgram.safeFloat("uTemperature", params.temperature)
        glProgram.safeFloat("uTint", params.tint)
        
        // Effects
        glProgram.safeFloat("uVignetteAmount", params.vignetteAmount)
        glProgram.safeFloat("uVignetteMidpoint", params.vignetteMidpoint)
        glProgram.safeFloat("uVignetteFeather", params.vignetteFeather)
        glProgram.safeFloat("uVignetteRoundness", params.vignetteRoundness)
        glProgram.safeFloat("uGrainAmount", params.grainAmount)
        glProgram.safeFloat("uGrainSize", params.grainSize)
        glProgram.safeFloat("uGrainRoughness", params.grainRoughness)
        glProgram.safeFloat("uTime", presentationTimeUs / 1_000_000f)
        
        // RGB Mix
        val mxR = params.rgbChannelMixer?.copyOfRange(0, 3) ?: floatArrayOf(1f, 0f, 0f)
        val mxG = params.rgbChannelMixer?.copyOfRange(3, 6) ?: floatArrayOf(0f, 1f, 0f)
        val mxB = params.rgbChannelMixer?.copyOfRange(6, 9) ?: floatArrayOf(0f, 0f, 1f)
        glProgram.safeFloat("uChannelMixerRR", mxR[0])
        glProgram.safeFloat("uChannelMixerRG", mxR[1])
        glProgram.safeFloat("uChannelMixerRB", mxR[2])
        glProgram.safeFloat("uChannelMixerGR", mxG[0])
        glProgram.safeFloat("uChannelMixerGG", mxG[1])
        glProgram.safeFloat("uChannelMixerGB", mxG[2])
        glProgram.safeFloat("uChannelMixerBR", mxB[0])
        glProgram.safeFloat("uChannelMixerBG", mxB[1])
        glProgram.safeFloat("uChannelMixerBB", mxB[2])
        
        glProgram.safeFloat("uChannelMultiplierR", params.redChannelMultiplier)
        glProgram.safeFloat("uChannelMultiplierG", params.greenChannelMultiplier)
        glProgram.safeFloat("uChannelMultiplierB", params.blueChannelMultiplier)
        
        glProgram.safeFloat("uChannelOffsetR", params.redOffset)
        glProgram.safeFloat("uChannelOffsetG", params.greenOffset)
        glProgram.safeFloat("uChannelOffsetB", params.blueOffset)
        
        // Color balance
        glProgram.safeFloat("uShadowBalanceR", params.shadowColorBalance.x)
        glProgram.safeFloat("uShadowBalanceG", params.shadowColorBalance.y)
        glProgram.safeFloat("uShadowBalanceB", params.shadowColorBalance.z)
        glProgram.safeFloat("uMidtoneBalanceR", params.midtoneColorBalance.x)
        glProgram.safeFloat("uMidtoneBalanceG", params.midtoneColorBalance.y)
        glProgram.safeFloat("uMidtoneBalanceB", params.midtoneColorBalance.z)
        glProgram.safeFloat("uHighlightBalanceR", params.highlightColorBalance.x)
        glProgram.safeFloat("uHighlightBalanceG", params.highlightColorBalance.y)
        glProgram.safeFloat("uHighlightBalanceB", params.highlightColorBalance.z)
        
        glProgram.safeFloat("uShadowLumaMin", params.shadowLumaRange[0])
        glProgram.safeFloat("uShadowLumaMax", params.shadowLumaRange[1])
        glProgram.safeFloat("uHighlightLumaMin", params.highlightLumaRange[0])
        glProgram.safeFloat("uHighlightLumaMax", params.highlightLumaRange[1])
        
        glProgram.safeFloat("uDehaze", params.dehaze)
        glProgram.safeFloat("uClarity", params.clarity)
        glProgram.safeFloat("uStructure", params.structure)
    }
}
