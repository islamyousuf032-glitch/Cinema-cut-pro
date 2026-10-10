package com.example.timeline.export.backend

import com.example.model.adjustments.AdjustmentKeyframe
import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.KeyframeInterpolation
import com.example.model.adjustments.TargetType
import com.example.model.adjustments.VideoAdjustmentParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ClipAdjustmentSupportTest {
    private fun stack(params: VideoAdjustmentParams = VideoAdjustmentParams.default()) =
        AdjustmentStack("stack", TargetType.CLIP, "clip", params = params)

    @Test
    fun screenshotPrimarySettingsAreSupportedAndPassedToNativeRenderer() {
        val params = VideoAdjustmentParams.default().copy(
            contrast = 0.20f,
            saturation = 1.20f,
            vibrance = 0.31f,
            temperature = 0.26f,
            tint = 0.40f,
            clarity = 0.03f
        )
        val adjustments = stack(params)

        assertTrue(Media3ClipAdjustmentSupport.validationErrors(adjustments, nativeEngineAvailable = true).isEmpty())
        assertTrue(Media3ClipAdjustmentSupport.hasRenderableAdjustments(adjustments))
        assertArrayEquals(
            floatArrayOf(0f, 0f, 0.20f, 1.20f, 0.31f, 0.26f, 0.40f, 0.015f),
            Media3ClipAdjustmentSupport.nativeParameters(params),
            0.0001f
        )
    }

    @Test
    fun defaultOrDisabledAdjustmentsDoNotRequireRendering() {
        val defaults = stack()
        assertTrue(Media3ClipAdjustmentSupport.validationErrors(defaults, nativeEngineAvailable = false).isEmpty())
        assertFalse(Media3ClipAdjustmentSupport.hasRenderableAdjustments(defaults))

        val disabled = stack(VideoAdjustmentParams.default().copy(grainAmount = 0.5f)).copy(enabled = false)
        assertTrue(Media3ClipAdjustmentSupport.validationErrors(disabled, nativeEngineAvailable = false).isEmpty())
        assertFalse(Media3ClipAdjustmentSupport.hasRenderableAdjustments(disabled))
    }

    @Test
    fun unsupportedOrAnimatedAdjustmentsAreRejectedInsteadOfDropped() {
        val withGrain = stack(VideoAdjustmentParams.default().copy(grainAmount = 0.25f))
        val grainErrors = Media3ClipAdjustmentSupport.validationErrors(withGrain, nativeEngineAvailable = true)
        assertTrue(grainErrors.any { it.contains("film grain") })

        val animated = stack(VideoAdjustmentParams.default().copy(contrast = 0.2f)).copy(
            keyframes = listOf(
                AdjustmentKeyframe(
                    keyframeId = "keyframe",
                    parameterId = "contrast",
                    frame = 12L,
                    value = 0.5f,
                    interpolation = KeyframeInterpolation.LINEAR
                )
            )
        )
        assertTrue(
            Media3ClipAdjustmentSupport.validationErrors(animated, nativeEngineAvailable = true)
                .any { it.contains("Animated clip adjustments") }
        )
    }

    @Test
    fun activeAdjustmentRequiresTheNativeProcessorAndValidRanges() {
        val contrast = stack(VideoAdjustmentParams.default().copy(contrast = 0.2f))
        assertTrue(
            Media3ClipAdjustmentSupport.validationErrors(contrast, nativeEngineAvailable = false)
                .any { it.contains("native color-adjustment engine is unavailable") }
        )

        val invalid = stack(VideoAdjustmentParams.default().copy(contrast = 1.5f))
        assertTrue(
            Media3ClipAdjustmentSupport.validationErrors(invalid, nativeEngineAvailable = true)
                .any { it.contains("outside their supported ranges") }
        )
    }
}
