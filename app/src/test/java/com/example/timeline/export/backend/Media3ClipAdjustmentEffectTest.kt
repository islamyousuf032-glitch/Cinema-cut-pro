package com.example.timeline.export.backend

import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.Vector3
import com.example.model.adjustments.engine.VideoAdjustmentEffect
import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ClipAdjustmentEffectTest {
    @Test
    fun exportUsesTheSameMutableGlslEffectAsPreview() {
        val params = VideoAdjustmentParams.default().copy(
            exposureStops = 0.7f,
            saturation = 1.25f,
            lift = Vector3(0.1f, -0.05f, 0.02f),
            curves = CurveParams().copy(
                masterPoints = listOf(CurvePoint(0f, 0f), CurvePoint(0.4f, 0.65f), CurvePoint(1f, 1f))
            )
        )

        val effect = createMedia3ClipAdjustmentEffect(params)

        assertTrue(effect is VideoAdjustmentEffect)
        assertEquals(params, (effect as VideoAdjustmentEffect).currentParams)
    }
}
