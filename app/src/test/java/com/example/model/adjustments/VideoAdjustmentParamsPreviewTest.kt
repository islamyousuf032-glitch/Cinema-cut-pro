package com.example.model.adjustments

import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAdjustmentParamsPreviewTest {
    @Test
    fun defaultParamsUseTheShaderFastPath() {
        assertFalse(VideoAdjustmentParams.default().hasActivePreviewEffect())
        assertFalse(
            VideoAdjustmentParams.default()
                .copy(temperatureKelvin = WhiteBalanceTemperature.NEUTRAL_KELVIN)
                .hasActivePreviewEffect()
        )
    }

    @Test
    fun visibleColorChangesEnableTheGradeShader() {
        assertTrue(VideoAdjustmentParams.default().copy(brightness = 0.1f).hasActivePreviewEffect())
        assertTrue(VideoAdjustmentParams.default().copy(saturation = 1.2f).hasActivePreviewEffect())
        assertTrue(VideoAdjustmentParams.default().copy(temperatureKelvin = 3_000f).hasActivePreviewEffect())
        assertTrue(VideoAdjustmentParams.default().copy(hslHue = floatArrayOf(0.1f)).hasActivePreviewEffect())
        assertTrue(VideoAdjustmentParams.default().copy(qualShowMatte = true).hasActivePreviewEffect())
        assertTrue(VideoAdjustmentParams.default().copy(lutId = "test-lut").hasActivePreviewEffect())
    }

    @Test
    fun nonDefaultCurvesEnablePreviewAndParticipateInEqualityAndHashCode() {
        val defaults = VideoAdjustmentParams.default()
        val curves = CurveParams().copy(
            masterPoints = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.7f), CurvePoint(1f, 1f))
        )
        val adjusted = defaults.copy(curves = curves)

        assertTrue(adjusted.hasActivePreviewEffect())
        assertNotEquals(defaults, adjusted)
        assertEquals(adjusted, adjusted.copy())
        assertEquals(adjusted.hashCode(), adjusted.copy().hashCode())
    }

    @Test
    fun identityMixerDoesNotEnableTheGradeShader() {
        val identityMixer = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertFalse(VideoAdjustmentParams.default().copy(rgbChannelMixer = identityMixer).hasActivePreviewEffect())
    }

    @Test
    fun qualifierParametersParticipateInEqualityAndHashCode() {
        val defaults = VideoAdjustmentParams.default()
        val adjusted = defaults.copy(qualEnabled = true, qualHueCenter = 0.25f, qualHueShift = 0.1f)

        assertNotEquals(defaults, adjusted)
        assertEquals(adjusted, adjusted.copy())
        assertEquals(adjusted.hashCode(), adjusted.copy().hashCode())
        assertTrue(adjusted.hasActivePreviewEffect())
    }
}
