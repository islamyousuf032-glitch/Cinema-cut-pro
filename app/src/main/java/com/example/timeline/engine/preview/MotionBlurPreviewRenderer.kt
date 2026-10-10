package com.example.timeline.engine.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeline.core.transform.ClipTransform

@Composable
fun MotionBlurPreviewRenderer(
    renderState: TransformRenderState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        if (renderState.transform.motionBlurParams.enabled) {
            // Ideally we do the real compose native blur or draw into offscreen bitmap and use nativeApplyTransformMotionBlurRgba8888 
            // In Android/Compose, realtime GPU motion blur is complex without custom shaders.
            // But we will show the honestly limitation/preview.
            // For now just show a note over the clip saying "GPU Compositor Required for Realtime Motion Blur"
            if (!renderState.requiresStillFrameFallback) {
                Text("Motion Blur On (GPU Compositor Required)", color = Color.Yellow)
            }
        }
    }
}
