package com.example.timeline.engine.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ViewportTransformLayer(
    renderState: TransformRenderState,
    modifier: Modifier = Modifier,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    content: @Composable () -> Unit
) {
    TransformCompositor(
        renderState = renderState,
        modifier = modifier,
        colorFilter = colorFilter,
        content = content
    )
}
