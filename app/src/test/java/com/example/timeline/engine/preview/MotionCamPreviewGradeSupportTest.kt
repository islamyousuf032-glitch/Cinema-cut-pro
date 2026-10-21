package com.example.timeline.engine.preview

import com.example.model.adjustments.Vector3
import com.example.model.adjustments.VideoAdjustmentParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionCamPreviewGradeSupportTest {
    @Test
    fun defaultsDoNotInvokeNativeBitmapGrading() {
        assertFalse(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(VideoAdjustmentParams.default()))
    }

    @Test
    fun exposureTemperatureTintAndPrimaryBalanceUseTheNativePath() {
        val defaults = VideoAdjustmentParams.default()
        val supportedChanges = listOf(
            defaults.copy(exposureStops = 0.5f),
            defaults.copy(temperature = 0.2f),
            defaults.copy(tint = -0.1f),
            defaults.copy(lift = Vector3(0.1f, 0f, 0f)),
            defaults.copy(gamma = Vector3(0.9f, 1f, 1f)),
            defaults.copy(gain = Vector3(1.1f, 1f, 1f)),
            defaults.copy(offsetWheel = Vector3(0.1f, 0f, 0f)),
            defaults.copy(shadowColorBalance = Vector3(0.1f, 0f, 0f)),
            defaults.copy(midtoneColorBalance = Vector3(0f, 0.1f, 0f)),
            defaults.copy(highlightColorBalance = Vector3(0f, 0f, 0.1f))
        )

        supportedChanges.forEach { params ->
            assertTrue(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(params))
        }
    }

    @Test
    fun basicViewportControlsAreLeftForComposeAndNotAppliedTwice() {
        val params = VideoAdjustmentParams.default().copy(
            brightness = 0.2f,
            contrast = 0.3f,
            saturation = 1.4f
        )

        assertFalse(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(params))
        val nativeParams = MotionCamPreviewGradeSupport.paramsForNativeBitmapProcessing(params)
        assertEquals(0f, nativeParams.brightness, 0f)
        assertEquals(0f, nativeParams.contrast, 0f)
        assertEquals(1f, nativeParams.saturation, 0f)
    }

    @Test
    fun advancedOnlyControlsRemainOutsideNativeBitmapPath() {
        val params = VideoAdjustmentParams.default().copy(
            hslHue = floatArrayOf(0.1f),
            lutId = "example-lut",
            qualEnabled = true
        )

        assertFalse(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(params))
    }
}
