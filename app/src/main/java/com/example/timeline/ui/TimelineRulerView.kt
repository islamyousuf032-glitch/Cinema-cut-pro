package com.example.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.example.timeline.core.Rational
import com.example.timeline.engine.native.NativeTimelineCore

@Composable
fun TimelineRulerView(
    scrollX: Float,
    viewportWidth: Int,
    pixelsPerFrame: Float,
    fpsRational: Rational,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Background
        drawRect(color = Color(0xFF141414))
        drawRect(color = Color.White.copy(alpha = 0.1f), topLeft = Offset(0f, h - 1f), size = androidx.compose.ui.geometry.Size(w, 1f))
        
        val fps = Math.round(fpsRational.toFloat()).toLong().coerceAtLeast(1)
        val startFrame = Math.max(0, (scrollX / pixelsPerFrame).toLong())
        val endFrame = ((scrollX + w) / pixelsPerFrame).toLong() + 1
        
        // Determine density based on zoom (pixelsPerFrame)
        // If pixelsPerFrame is large (zoomed in), we show frame-level ticks
        // If pixelsPerFrame is small (zoomed out), we show seconds or minutes
        
        var minorInterval = 1L
        var majorInterval = fps
        
        if (pixelsPerFrame > 20f) {
            minorInterval = 1L
            majorInterval = fps
        } else if (pixelsPerFrame > 5f) {
            minorInterval = fps / 2L
            majorInterval = fps
        } else if (pixelsPerFrame > 1f) {
            minorInterval = fps
            majorInterval = fps * 10L
        } else if (pixelsPerFrame > 0.1f) {
            minorInterval = fps * 10L
            majorInterval = fps * 60L
        } else {
            minorInterval = fps * 60L
            majorInterval = fps * 300L
        }
        
        minorInterval = minorInterval.coerceAtLeast(1)
        majorInterval = majorInterval.coerceAtLeast(1)
        
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 24f
            isAntiAlias = true
        }

        if (NativeTimelineCore.isAvailable()) {
            val ticks = NativeTimelineCore.nativeCalculateRulerTicks(scrollX, viewportWidth, pixelsPerFrame, fps.toInt())
            var i = 0
            while (i < ticks.size) {
                val frame = ticks[i].toLong()
                val tx = ticks[i + 1]
                val isMajor = ticks[i + 2] > 0.5f
                
                if (isMajor) {
                    drawLine(Color.White.copy(alpha = 0.8f), start = Offset(tx, h * 0.4f), end = Offset(tx, h), strokeWidth = 2f)
                    val timeStr = TimecodeFormatter.formatTimecode(frame, fpsRational)
                    drawContext.canvas.nativeCanvas.drawText(timeStr, tx + 4f, h * 0.6f, paint)
                } else {
                    drawLine(Color.White.copy(alpha = 0.3f), start = Offset(tx, h * 0.7f), end = Offset(tx, h), strokeWidth = 2f)
                }
                i += 3
            }
        } else {
            val firstTick = (startFrame / minorInterval) * minorInterval
            for (f in firstTick..endFrame step minorInterval) {
                val tx = f * pixelsPerFrame - scrollX
                val isMajor = f % majorInterval == 0L
                
                if (isMajor) {
                    drawLine(Color.White.copy(alpha = 0.8f), start = Offset(tx, h * 0.4f), end = Offset(tx, h), strokeWidth = 2f)
                    val timeStr = TimecodeFormatter.formatTimecode(f, fpsRational)
                    drawContext.canvas.nativeCanvas.drawText(timeStr, tx + 4f, h * 0.6f, paint)
                } else {
                    drawLine(Color.White.copy(alpha = 0.3f), start = Offset(tx, h * 0.7f), end = Offset(tx, h), strokeWidth = 2f)
                }
            }
        }
    }
}
