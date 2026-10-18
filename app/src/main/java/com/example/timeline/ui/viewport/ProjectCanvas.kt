package com.example.timeline.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ProjectCanvas(
    projectBackgroundColor: Color,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black), // letterbox/pillarbox background outside canvas
        contentAlignment = Alignment.Center
    ) {
        AspectRatioPreviewBox(
            aspectRatio = aspectRatio,
            backgroundColor = projectBackgroundColor,
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
