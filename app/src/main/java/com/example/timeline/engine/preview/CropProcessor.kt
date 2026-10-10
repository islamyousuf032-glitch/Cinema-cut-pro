package com.example.timeline.engine.preview

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.timeline.core.transform.CropParams

class CropProcessor(private val params: CropParams) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val left = params.cropLeft * size.width
        val top = params.cropTop * size.height
        val right = size.width - (params.cropRight * size.width)
        val bottom = size.height - (params.cropBottom * size.height)

        val safeLeft = left.coerceAtMost(right)
        val safeTop = top.coerceAtMost(bottom)
        
        return Outline.Rectangle(
            Rect(
                left = safeLeft,
                top = safeTop,
                right = right.coerceAtLeast(safeLeft),
                bottom = bottom.coerceAtLeast(safeTop)
            )
        )
    }
}
