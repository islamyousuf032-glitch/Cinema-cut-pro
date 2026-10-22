package com.example.timeline.engine.preview

import com.example.model.adjustments.Vector3
import com.example.model.adjustments.VideoAdjustmentParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionCamPreviewGradeSupportTest {
    @Test
    fun defaultsDoNotAllocateNativeGradePayload() {
        val defaults = VideoAdjustmentParams.default()
        assertFalse(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(defaults))
        assertNull(MotionCamPreviewGradeSupport.nativeGradeParams(defaults))
    }

    @Test
    fun supportedPrimaryChangesUseTheNativeRendererPath() {
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
            assertEquals(15, MotionCamPreviewGradeSupport.nativeGradeParams(params)?.size)
        }
    }

    @Test
    fun primaryAndZoneControlsAreFoldedIntoTheNativePayload() {
        val params = VideoAdjustmentParams.default().copy(
            exposureStops = 0.75f,
            temperature = 0.2f,
            tint = -0.1f,
            lift = Vector3(0.1f, 0.2f, 0.3f),
            gamma = Vector3(0.9f, 1.0f, 1.1f),
            gain = Vector3(1.2f, 1.1f, 1.0f),
            offsetWheel = Vector3(0.01f, 0.02f, 0.03f),
            shadowColorBalance = Vector3(0.2f, 0f, -0.2f),
            midtoneColorBalance = Vector3(0f, 0.2f, -0.2f),
            highlightColorBalance = Vector3(0.1f, -0.1f, 0f)
        )

        val payload = MotionCamPreviewGradeSupport.nativeGradeParams(params)
        assertNotNull(payload)
        val values = requireNotNull(payload)
        assertEquals(0.75f, values[0], 0f)
        assertEquals(0.2f, values[1], 0f)
        assertEquals(-0.1f, values[2], 0f)
        assertEquals(0.2f, values[3], 0.00001f)
        assertEquals(0.2f, values[4], 0.00001f)
        assertEquals(0.2f, values[5], 0.00001f)
        assertEquals(0.9f, values[6], 0.00001f)
        assertEquals(0.9f, values[7], 0.00001f)
        assertEquals(1.21f, values[8], 0.00001f)
        assertEquals(1.26f, values[9], 0.00001f)
        assertEquals(1.045f, values[10], 0.00001f)
        assertEquals(1.0f, values[11], 0f)
        assertEquals(0.01f, values[12], 0f)
        assertEquals(0.02f, values[13], 0f)
        assertEquals(0.03f, values[14], 0f)
    }

    @Test
    fun basicViewportControlsStayInComposeAndAreNotAppliedTwice() {
        val params = VideoAdjustmentParams.default().copy(
            brightness = 0.2f,
            contrast = 0.3f,
            saturation = 1.4f
        )

        assertFalse(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(params))
        assertNull(MotionCamPreviewGradeSupport.nativeGradeParams(params))
    }

    @Test
    fun advancedOnlyControlsRemainOutsideTheNativeRawPath() {
        val params = VideoAdjustmentParams.default().copy(
            hslHue = floatArrayOf(0.1f),
            lutId = "example-lut",
            qualEnabled = true
        )

        assertFalse(MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(params))
        assertNull(MotionCamPreviewGradeSupport.nativeGradeParams(params))
    }
}
