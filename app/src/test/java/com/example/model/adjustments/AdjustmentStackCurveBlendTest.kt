package com.example.model.adjustments

import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustmentStackCurveBlendTest {
    @Test
    fun mergingAdjustmentLayerBlendsAndPreservesCurveData() {
        val base = AdjustmentStack(
            stackId = "base",
            targetType = TargetType.CLIP,
            targetId = "clip"
        )
        val overlayCurves = CurveParams().copy(
            masterPoints = listOf(CurvePoint(0f, 0.4f), CurvePoint(1f, 0.6f))
        )
        val overlay = AdjustmentStack(
            stackId = "overlay",
            targetType = TargetType.CLIP,
            targetId = "clip",
            params = VideoAdjustmentParams.default().copy(curves = overlayCurves),
            opacity = 0.5f
        )

        val merged = base.mergeWithAdjustmentLayer(overlay)

        assertEquals(0.2f, merged.curves.masterPoints.first().y, 0.0001f)
        assertEquals(0.8f, merged.curves.masterPoints.last().y, 0.0001f)
    }
}
