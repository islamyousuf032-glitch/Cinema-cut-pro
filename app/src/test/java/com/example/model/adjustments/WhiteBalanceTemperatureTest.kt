package com.example.model.adjustments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteBalanceTemperatureTest {
    @Test
    fun neutralKelvinDoesNotShiftRelativeTemperature() {
        assertEquals(
            0f,
            WhiteBalanceTemperature.resolve(0f, WhiteBalanceTemperature.NEUTRAL_KELVIN),
            0.0001f
        )
        assertEquals(
            0.25f,
            WhiteBalanceTemperature.resolve(0.25f, WhiteBalanceTemperature.NEUTRAL_KELVIN),
            0.0001f
        )
    }

    @Test
    fun warmerAndCoolerKelvinMapToOppositeRelativeDirections() {
        val warm = WhiteBalanceTemperature.resolve(0f, 3_000f)
        val cool = WhiteBalanceTemperature.resolve(0f, 10_000f)

        assertTrue(warm > 0f)
        assertTrue(cool < 0f)
        assertEquals(1f, WhiteBalanceTemperature.resolve(0f, 2_000f), 0.0001f)
        assertEquals(-1f, WhiteBalanceTemperature.resolve(0f, 50_000f), 0.0001f)
    }

    @Test
    fun relativeTemperatureCombinesAndClampsWithKelvinAdjustment() {
        assertEquals(1f, WhiteBalanceTemperature.resolve(0.75f, 3_000f), 0.0001f)
        assertEquals(-1f, WhiteBalanceTemperature.resolve(-0.75f, 50_000f), 0.0001f)
    }
}
