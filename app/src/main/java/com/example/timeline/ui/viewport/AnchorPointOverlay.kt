package com.example.timeline.ui.viewport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawAnchorPointOverlay(
    anchorX: Float,
    anchorY: Float,
    color: Color = Color.Red
) {
    val radius = 6f / density
    val center = Offset(anchorX, anchorY)
    
    // Draw crosshair
    drawLine(
        color = color,
        start = Offset(anchorX - radius * 2, anchorY),
        end = Offset(anchorX + radius * 2, anchorY),
        strokeWidth = 2f / density
    )
    drawLine(
        color = color,
        start = Offset(anchorX, anchorY - radius * 2),
        end = Offset(anchorX, anchorY + radius * 2),
        strokeWidth = 2f / density
    )
    
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = 2f / density)
    )
}
