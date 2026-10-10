package com.example.timeline.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AspectRatioPreviewBox(
    aspectRatio: Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        val parentRatio = if (maxHeight.value > 0) maxWidth.value / maxHeight.value else 1f
        val matchHeightFirst = aspectRatio <= parentRatio

        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = matchHeightFirst)
                .background(backgroundColor)
        ) {
            content()
        }
    }
}
