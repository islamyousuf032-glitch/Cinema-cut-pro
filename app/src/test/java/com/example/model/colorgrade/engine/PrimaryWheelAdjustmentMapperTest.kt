package com.example.model.colorgrade.engine

import com.example.model.adjustments.Vector3
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.colorgrade.ColorGradeParams
import com.example.model.colorgrade.ColorWheelParams
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryWheelAdjustmentMapperTest {
    private fun assertVector(expected: Vector3, actual: Vector3) {
        assertEquals(expected.x, actual.x, 0.00001f)
        assertEquals(expected.y, actual.y, 0.00001f)
        assertEquals(expected.z, actual.z, 0.00001f)
    }

    @Test
    fun neutralGradeMapsToNeutralShaderUniforms() {
        val mapped = PrimaryWheelAdjustmentMapper.apply(
            VideoAdjustmentParams.default(),
            ColorGradeParams()
        )

        assertVector(Vector3.ZERO, mapped.lift)
        assertVector(Vector3.ONE, mapped.gamma)
        assertVector(Vector3.ONE, mapped.gain)
        assertVector(Vector3.ZERO, mapped.offsetWheel)
    }

    @Test
    fun wheelLumaIsAppliedEquallyAndGammaRemainsInSafeRange() {
        val grade = ColorGradeParams(
            lift = ColorWheelParams(r = 0.4f, g = -0.3f, b = 0.05f, luma = 0.2f),
            gamma = ColorWheelParams(r = 0.2f, g = -0.2f, b = 0.5f, luma = -0.1f),
            gain = ColorWheelParams(r = 0.1f, g = -0.2f, b = 0.3f, luma = 0.2f),
            offset = ColorWheelParams(r = 0.4f, g = -0.3f, b = 0.05f, luma = 0.2f)
        )

        val mapped = PrimaryWheelAdjustmentMapper.apply(VideoAdjustmentParams.default(), grade)

        assertVector(Vector3(0.6f, -0.1f, 0.25f), mapped.lift)
        assertVector(Vector3(1.1f, 0.7f, 1.4f), mapped.gamma)
        assertVector(Vector3(1.3f, 1.0f, 1.5f), mapped.gain)
        assertVector(Vector3(0.6f, -0.1f, 0.25f), mapped.offsetWheel)
    }

    @Test
    fun primaryGradeUpdatesDoNotResetUnchangedSharedAdjustmentValues() {
        val base = VideoAdjustmentParams.default().copy(
            temperature = 0.4f,
            saturation = 1.6f,
            contrast = -0.25f
        )
        val newGrade = ColorGradeParams(
            exposure = 0.7f,
            lift = ColorWheelParams(r = 0.2f),
            saturation = 1.3f
        )

        val mapped = PrimaryWheelAdjustmentMapper.applyChanges(
            base = base,
            previousGrade = ColorGradeParams(),
            newGrade = newGrade
        )

        assertEquals(0.7f, mapped.exposureStops, 0.00001f)
        assertVector(Vector3(0.2f, 0f, 0f), mapped.lift)
        assertEquals(1.3f, mapped.saturation, 0.00001f)
        assertEquals(0.4f, mapped.temperature, 0.00001f)
    }

    @Test
    fun nonPrimaryGradeUpdatesPreserveDirectPrimarySettings() {
        val base = VideoAdjustmentParams.default().copy(
            temperature = 0.35f,
            contrast = 0.2f,
            tint = -0.1f
        )

        val mapped = PrimaryWheelAdjustmentMapper.applyChanges(
            base = base,
            previousGrade = ColorGradeParams(),
            newGrade = ColorGradeParams()
        )

        assertEquals(base, mapped)
    }

    @Test
    fun gammaAndOtherWheelValuesAreClampedToShaderSafeBounds() {
        val grade = ColorGradeParams(
            lift = ColorWheelParams(r = 2f, g = -2f, b = 0f),
            gamma = ColorWheelParams(r = -2f, g = 2f, b = 0f),
            gain = ColorWheelParams(r = -2f, g = 2f, b = 0f),
            offset = ColorWheelParams(r = 2f, g = -2f, b = 0f)
        )

        val mapped = PrimaryWheelAdjustmentMapper.apply(VideoAdjustmentParams.default(), grade)

        assertVector(Vector3(1f, -1f, 0f), mapped.lift)
        assertVector(Vector3(0.1f, 2f, 1f), mapped.gamma)
        assertVector(Vector3(0f, 2f, 1f), mapped.gain)
        assertVector(Vector3(1f, -1f, 0f), mapped.offsetWheel)
    }
}
