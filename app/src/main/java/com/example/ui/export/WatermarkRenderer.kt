package com.example.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.timeline.export.model.WatermarkSettings
import com.example.timeline.export.model.WatermarkPosition
import java.nio.ByteBuffer

object WatermarkRenderer {

    fun createWatermarkBitmap(context: Context, settings: WatermarkSettings, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (settings.isText) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = (settings.opacity * 255).toInt()
                textSize = height * (settings.sizePercent / 100f)
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
            }

            val margin = height * (settings.marginPercent / 100f)
            val textWidth = paint.measureText(settings.text)
            val fontMetrics = paint.fontMetrics
            val textHeight = fontMetrics.descent - fontMetrics.ascent

            var x = margin
            var y = margin - fontMetrics.ascent

            when (settings.position) {
                WatermarkPosition.TOP_LEFT -> {
                    x = margin
                    y = margin - fontMetrics.ascent
                }
                WatermarkPosition.TOP_RIGHT -> {
                    x = width - textWidth - margin
                    y = margin - fontMetrics.ascent
                }
                WatermarkPosition.BOTTOM_LEFT -> {
                    x = margin
                    y = height - margin - fontMetrics.bottom
                }
                WatermarkPosition.BOTTOM_RIGHT -> {
                    x = width - textWidth - margin
                    y = height - margin - fontMetrics.bottom
                }
                WatermarkPosition.CENTER -> {
                    x = (width - textWidth) / 2f
                    y = (height - textHeight) / 2f - fontMetrics.ascent
                }
            }

            canvas.drawText(settings.text, x, y, paint)
        }

        return bitmap
    }

    fun getWatermarkRgbaBuffer(context: Context, settings: WatermarkSettings, width: Int, height: Int): ByteBuffer {
        val bitmap = createWatermarkBitmap(context, settings, width, height)
        val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        bitmap.recycle()
        return buffer
    }
}
