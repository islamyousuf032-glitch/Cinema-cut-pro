package com.example.model.adjustments.engine

import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCurveTextureDataTest {
    @Test
    fun identityCurvesCreateThreeRowRgbaTexture() {
        val data = VideoCurveTextureData.createRgbaLut(CurveParams())
        val sampleIndex = 64
        val topRow = sampleIndex * 4
        val middleRow = (VideoCurveTextureData.WIDTH + sampleIndex) * 4
        val bottomRow = (VideoCurveTextureData.WIDTH * 2 + sampleIndex) * 4

        assertEquals(VideoCurveTextureData.WIDTH * VideoCurveTextureData.HEIGHT * 4, data.size)
        assertEquals(sampleIndex.toByte(), data[topRow])
        assertEquals(sampleIndex.toByte(), data[topRow + 1])
        assertEquals(sampleIndex.toByte(), data[topRow + 2])
        assertEquals(sampleIndex.toByte(), data[topRow + 3])
        assertEquals(sampleIndex.toByte(), data[middleRow])
        assertEquals(128.toByte(), data[middleRow + 1])
        assertEquals(128.toByte(), data[middleRow + 2])
        assertEquals(128.toByte(), data[middleRow + 3])
        assertEquals(128.toByte(), data[bottomRow])
        assertEquals(128.toByte(), data[bottomRow + 1])
        assertTrue(VideoCurveTextureData.validationErrors(CurveParams()).isEmpty())
    }

    @Test
    fun enabledMaskIncludesEveryCurveFamily() {
        val defaults = CurveParams()
        val changed = defaults.copy(
            redPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0.8f)),
            lumaPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0.9f)),
            hueVsHuePoints = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.7f)),
            satVsSatPoints = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.8f))
        )

        assertEquals(
            VideoCurveTextureData.RGB_CURVES or VideoCurveTextureData.LUMA_CURVE or
                VideoCurveTextureData.HUE_CURVES or VideoCurveTextureData.SATURATION_CURVES,
            VideoCurveTextureData.enabledMask(changed)
        )
    }

    @Test
    fun malformedOrOversizedCurvesAreRejectedForExportAndSanitizedForPreview() {
        val malformed = CurveParams().copy(
            masterPoints = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, Float.NaN), CurvePoint(0.5f, 1f))
        )
        val oversized = CurveParams().copy(
            redPoints = List(VideoCurveTextureData.MAX_CONTROL_POINTS + 1) { index ->
                CurvePoint(index.toFloat() / VideoCurveTextureData.MAX_CONTROL_POINTS, 0.5f)
            }
        )

        assertTrue(VideoCurveTextureData.validationErrors(malformed).isNotEmpty())
        assertTrue(VideoCurveTextureData.validationErrors(oversized).any { it.contains("more than") })
        assertEquals(
            VideoCurveTextureData.WIDTH * VideoCurveTextureData.HEIGHT * 4,
            VideoCurveTextureData.createRgbaLut(malformed).size
        )
    }
}
