package com.example.ui.colorgrade

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.atan2
import kotlin.math.hypot

object ColorWheelBitmapGenerator {
    fun generateCpuBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val center = size / 2f
        val radius = size / 2f
        
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val dist = hypot(dx, dy)
                if (dist <= radius) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    val color = Color.HSVToColor(floatArrayOf(angle, 1f, 1f))
                    // Add 30% alpha like the C++ code to match the UI style
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)
                    val a = 255
                    pixels[y * size + x] = Color.argb(a, r, g, b)
                } else {
                    pixels[y * size + x] = Color.TRANSPARENT
                }
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }
}
