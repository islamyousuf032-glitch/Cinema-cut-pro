package com.example.timeline.ui.viewport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawBoundingBoxOverlay(
    width: Float,
    height: Float,
    color: Color = Color.Cyan
) {
    drawRect(
        color = color,
        topLeft = Offset(-width / 2f, -height / 2f),
        size = Size(width, height),
        style = Stroke(width = 2f / density)
    )
    
    // Draw corner handles
    val handleSize = 10f / density
    val corners = listOf(
        Offset(-width / 2f, -height / 2f),
        Offset(width / 2f, -height / 2f),
        Offset(width / 2f, height / 2f),
        Offset(-width / 2f, height / 2f)
    )
    
    corners.forEach { corner ->
        drawRect(
            color = Color.White,
            topLeft = Offset(corner.x - handleSize/2, corner.y - handleSize/2),
            size = Size(handleSize, handleSize)
        )
        drawRect(
            color = color,
            topLeft = Offset(corner.x - handleSize/2, corner.y - handleSize/2),
            size = Size(handleSize, handleSize),
            style = Stroke(width = 1f / density)
        )
    }
}
