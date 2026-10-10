package com.example.timeline.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class MiniViewportController {
    var width by mutableStateOf(240.dp)
    var height by mutableStateOf(135.dp)
    
    fun resize(deltaX: Float, deltaY: Float, density: Float) {
        val minWidth = 160f
        val maxWidth = 640f
        
        val currentWidthPx = width.value * density
        // Since it's anchored at TopEnd, pulling BottomStart left (negative deltaX) increases width
        val newWidthPx = (currentWidthPx - deltaX).coerceIn(minWidth, maxWidth)
        val newHeightPx = newWidthPx * 9f / 16f
        
        width = (newWidthPx / density).dp
        height = (newHeightPx / density).dp
    }
}
