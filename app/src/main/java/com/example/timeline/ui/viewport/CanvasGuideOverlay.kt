package com.example.timeline.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun CanvasGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val color = Color.White.copy(alpha = 0.3f)
        val strokeW = 2f
        
        // Center lines
        drawLine(
            color = color,
            start = Offset(width / 2f, 0f),
            end = Offset(width / 2f, height),
            strokeWidth = strokeW
        )
        drawLine(
            color = color,
            start = Offset(0f, height / 2f),
            end = Offset(width, height / 2f),
            strokeWidth = strokeW
        )
    }
}
