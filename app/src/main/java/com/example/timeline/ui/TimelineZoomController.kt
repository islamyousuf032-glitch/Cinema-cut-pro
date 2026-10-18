package com.example.timeline.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class TimelineZoomController(
    val onZoomIn: () -> Unit,
    val onZoomOut: () -> Unit,
    val onSetZoom: (Float) -> Unit
)

@Composable
fun rememberTimelineZoomController(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onSetZoom: (Float) -> Unit
): TimelineZoomController {
    return remember(onZoomIn, onZoomOut, onSetZoom) {
        TimelineZoomController(onZoomIn, onZoomOut, onSetZoom)
    }
}
