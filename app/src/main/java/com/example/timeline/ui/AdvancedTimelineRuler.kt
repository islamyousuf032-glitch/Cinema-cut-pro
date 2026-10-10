package com.example.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.timeline.engine.native.advanced.NativeAdvancedTimelineCore

@Composable
fun AdvancedTimelineRuler(
    scrollX: Float,
    viewportWidth: Int,
    pixelsPerFrame: Float,
    frameRate: com.example.timeline.core.Rational,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        val width = size.width
        val height = size.height

        // Draw bottom border
        drawLine(
            color = Color.DarkGray,
            start = Offset(0f, height),
            end = Offset(width, height),
            strokeWidth = 1.dp.toPx()
        )

        if (NativeAdvancedTimelineCore.isAvailable()) {
            val ticks = NativeAdvancedTimelineCore.nativeCalculateRulerTicks(scrollX, width.toInt(), pixelsPerFrame, frameRate.toFloat().toInt())
            var i = 0
            while (i + 1 < ticks.size) {
                val tickX = ticks[i]
                val type = ticks[i + 1].toInt()
                
                if (tickX in 0f..width) {
                    val isMajor = type == 2
                    val tickHeight = if (isMajor) height * 0.5f else height * 0.25f
                    val yStart = height - tickHeight
                    
                    drawLine(
                        color = Color.Gray,
                        start = Offset(tickX, yStart),
                        end = Offset(tickX, height),
                        strokeWidth = 1f
                    )
                    
                    if (isMajor) {
                        val frame = NativeAdvancedTimelineCore.xToFrame(tickX + scrollX, pixelsPerFrame)
                        val timeStr = TimecodeFormatter.formatTimecode(frame, frameRate)
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            timeStr,
                            tickX + 4.dp.toPx(),
                            height - tickHeight - 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.LTGRAY
                                textSize = 10.dp.toPx()
                                isAntiAlias = true
                            }
                        )
                    }
                }
                i += 2
            }
        } else {
            android.util.Log.w("AdvancedTimelineRuler", "Using Kotlin fallback for ruler ticks")
            val fps = frameRate.toFloat().toInt().coerceAtLeast(1)
            val stepFrames = if (pixelsPerFrame * fps < 5f) fps * 10 else if (pixelsPerFrame * fps < 20f) fps * 5 else fps
            val startFrame = ((scrollX / pixelsPerFrame).toLong() / stepFrames) * stepFrames
            val endFrame = ((scrollX + width) / pixelsPerFrame).toLong() + 1
            
            for (f in startFrame..endFrame step stepFrames.toLong()) {
                val tickX = NativeAdvancedTimelineCore.frameToX(f, pixelsPerFrame) - scrollX
                val isMajor = (f % (fps * 10)) == 0L
                val tickHeight = if (isMajor) height * 0.5f else height * 0.25f
                drawLine(
                    color = Color.Gray,
                    start = Offset(tickX, height - tickHeight),
                    end = Offset(tickX, height),
                    strokeWidth = 1f
                )
                if (isMajor) {
                    val timeStr = TimecodeFormatter.formatTimecode(f, frameRate)
                    drawContext.canvas.nativeCanvas.drawText(
                        timeStr, tickX + 4.dp.toPx(), height - tickHeight - 4.dp.toPx(),
                        android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 10.dp.toPx(); isAntiAlias = true }
                    )
                }
            }
        }
    }
}
