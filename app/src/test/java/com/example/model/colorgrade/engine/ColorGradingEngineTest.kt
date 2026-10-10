package com.example.model.colorgrade.engine

import android.graphics.Bitmap
import com.example.model.colorgrade.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorGradingEngineTest {

    @Test
    fun testPrimaryCorrectorLift() {
        val rgb = floatArrayOf(0.1f, 0.1f, 0.1f)
        val params = ColorGradeParams(
            lift = ColorWheelParams(0.1f, 0f, 0f)
        )
        PrimaryColorCorrector.process(rgb, params)
        // r should be boosted
        assertNotEquals(0.1f, rgb[0])
        assertEquals(0.1f, rgb[1])
        assertEquals(0.1f, rgb[2])
    }
    
    @Test
    fun testCpuReferenceProcessor() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, 0xFF808080.toInt()) // 50% gray
        
        val stack = ColorGradeStack(
            targetType = ColorGradeTarget.CLIP,
            targetId = "test_clip",
            primaryCorrections = ColorGradeParams(
                gain = ColorWheelParams(0.5f, 0f, 0f) // boost red gain
            )
        )
        
        val processed = CpuColorGradeReferenceProcessor.process(bitmap, stack)
        val pixel = processed.getPixel(0, 0)
        
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        // Assert Red has been boosted significantly over green and blue
        println("R: $r G: $g B: $b")
        assert(r > g)
        assertEquals(128, g)
        assertEquals(128, b)
    }
}
