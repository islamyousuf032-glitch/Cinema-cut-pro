package com.example.timeline.export.backend

import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.Vector3
import com.example.model.adjustments.engine.VideoAdjustmentEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ClipAdjustmentEffectTest {
    @Test
    fun exportUsesTheSameMutableGlslEffectAsPreview() {
        val params = VideoAdjustmentParams.default().copy(
            exposureStops = 0.7f,
            saturation = 1.25f,
            lift = Vector3(0.1f, -0.05f, 0.02f)
        )

        val effect = createMedia3ClipAdjustmentEffect(params)

        assertTrue(effect is VideoAdjustmentEffect)
        assertEquals(params, (effect as VideoAdjustmentEffect).currentParams)
    }
}
