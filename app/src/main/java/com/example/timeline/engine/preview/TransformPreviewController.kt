package com.example.timeline.engine.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.timeline.core.transform.ClipTransform

@Composable
fun TransformPreviewController(
    transform: ClipTransform,
    onTransformChange: (ClipTransform) -> Unit,
    showCropHandles: Boolean = false,
    showPerspectiveHandles: Boolean = false,
    showAnchor: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (com.example.timeline.engine.NativeTransformEngine.isAvailable()) {
        val matrix = com.example.timeline.engine.NativeTransformEngine.buildTransformMatrix(transform)
    }
    Canvas(modifier = modifier.fillMaxSize().pointerInput(Unit) {
        // Implement drag logic
        detectDragGestures { change, dragAmount ->
            change.consume()
            // TBD: dispatch updates to onTransformChange
        }
    }) {
        val w = size.width
        val h = size.height

        // Draw Crop Handles
        if (showCropHandles) {
            val left = transform.cropParams.cropLeft * w
            val top = transform.cropParams.cropTop * h
            val right = w - (transform.cropParams.cropRight * w)
            val bottom = h - (transform.cropParams.cropBottom * h)

            drawRect(
                color = Color.Cyan,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 4f)
            )
            // Draw Handles
            drawCircle(Color.White, radius = 16f, center = Offset(left, (top + bottom) / 2))
            drawCircle(Color.White, radius = 16f, center = Offset(right, (top + bottom) / 2))
            drawCircle(Color.White, radius = 16f, center = Offset((left + right) / 2, top))
            drawCircle(Color.White, radius = 16f, center = Offset((left + right) / 2, bottom))
        }

        // Draw Perspective Handles
        if (showPerspectiveHandles && transform.perspectiveParams.enabled) {
            val p = transform.perspectiveParams
            val p1 = Offset(p.topLeftX * w, p.topLeftY * h)
            val p2 = Offset(p.topRightX * w, p.topRightY * h)
            val p3 = Offset(p.bottomRightX * w, p.bottomRightY * h)
            val p4 = Offset(p.bottomLeftX * w, p.bottomLeftY * h)

            drawLine(Color.Green, p1, p2, strokeWidth = 4f)
            drawLine(Color.Green, p2, p3, strokeWidth = 4f)
            drawLine(Color.Green, p3, p4, strokeWidth = 4f)
            drawLine(Color.Green, p4, p1, strokeWidth = 4f)

            drawCircle(Color.White, radius = 16f, center = p1)
            drawCircle(Color.White, radius = 16f, center = p2)
            drawCircle(Color.White, radius = 16f, center = p3)
            drawCircle(Color.White, radius = 16f, center = p4)
        }

        // Draw Anchor
        if (showAnchor) {
            val ax = transform.anchorPointX * w
            val ay = transform.anchorPointY * h
            drawCircle(Color.Red, radius = 12f, center = Offset(ax, ay), style = Stroke(width = 4f))
            drawLine(Color.Red, Offset(ax - 20f, ay), Offset(ax + 20f, ay), strokeWidth = 4f)
            drawLine(Color.Red, Offset(ax, ay - 20f), Offset(ax, ay + 20f), strokeWidth = 4f)
        }
    }
}
