package com.example.timeline.ui.viewport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.timeline.core.transform.PerspectiveParams

fun DrawScope.drawPerspectiveOverlay(
    baseWidth: Float,
    baseHeight: Float,
    perspectiveParams: PerspectiveParams,
    color: Color = Color.Yellow
) {
    if (!perspectiveParams.enabled) return
    
    val hw = baseWidth / 2f
    val hh = baseHeight / 2f
    
    val tl = Offset(-hw + perspectiveParams.topLeftX, -hh + perspectiveParams.topLeftY)
    val tr = Offset(hw + perspectiveParams.topRightX, -hh + perspectiveParams.topRightY)
    val bl = Offset(-hw + perspectiveParams.bottomLeftX, hh + perspectiveParams.bottomLeftY)
    val br = Offset(hw + perspectiveParams.bottomRightX, hh + perspectiveParams.bottomRightY)
    
    val path = Path().apply {
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(br.x, br.y)
        lineTo(bl.x, bl.y)
        close()
    }
    
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2f / density)
    )
    
    // Draw corner handles
    val radius = 8f / density
    val corners = listOf(tl, tr, bl, br)
    
    corners.forEach { corner ->
        drawCircle(color = Color.White, radius = radius, center = corner)
        drawCircle(color = color, radius = radius, center = corner, style = Stroke(width = 1f / density))
    }
}
