package com.example.timeline.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SafeAreaOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Action Safe: 5% margins => 90% of screen
        val actionSafeRectX = width * 0.05f
        val actionSafeRectY = height * 0.05f
        val actionSafeRectW = width * 0.9f
        val actionSafeRectH = height * 0.9f

        // Title Safe: 10% margins => 80% of screen
        val titleSafeRectX = width * 0.1f
        val titleSafeRectY = height * 0.1f
        val titleSafeRectW = width * 0.8f
        val titleSafeRectH = height * 0.8f

        drawRect(
            color = Color.Green.copy(alpha = 0.5f),
            topLeft = Offset(actionSafeRectX, actionSafeRectY),
            size = Size(actionSafeRectW, actionSafeRectH),
            style = Stroke(width = 2f)
        )

        drawRect(
            color = Color.Yellow.copy(alpha = 0.5f),
            topLeft = Offset(titleSafeRectX, titleSafeRectY),
            size = Size(titleSafeRectW, titleSafeRectH),
            style = Stroke(width = 2f)
        )
    }
}
