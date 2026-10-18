package com.example.timeline.ui.viewport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.timeline.core.transform.CropParams

fun DrawScope.drawCropOverlay(
    baseWidth: Float,
    baseHeight: Float,
    cropParams: CropParams,
    color: Color = Color.Magenta
) {
    val left = -baseWidth / 2f + cropParams.cropLeft
    val right = baseWidth / 2f - cropParams.cropRight
    val top = -baseHeight / 2f + cropParams.cropTop
    val bottom = baseHeight / 2f - cropParams.cropBottom
    
    val width = right - left
    val height = bottom - top
    
    if (width > 0 && height > 0) {
        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(width = 2f / density)
        )
        
        // Draw edge handles
        val handleSize = 12f / density
        val handleLength = 30f / density
        
        // Left
        drawRect(color, Offset(left - handleSize/2, top + height/2 - handleLength/2), Size(handleSize, handleLength))
        // Right
        drawRect(color, Offset(right - handleSize/2, top + height/2 - handleLength/2), Size(handleSize, handleLength))
        // Top
        drawRect(color, Offset(left + width/2 - handleLength/2, top - handleSize/2), Size(handleLength, handleSize))
        // Bottom
        drawRect(color, Offset(left + width/2 - handleLength/2, bottom - handleSize/2), Size(handleLength, handleSize))
    }
}
