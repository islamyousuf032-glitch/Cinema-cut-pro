package com.example.timeline.engine.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.timeline.core.transform.ClipTransform

import com.example.model.adjustments.AdjustmentStack
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

@Composable
fun PlayerViewContainer(
    previewEngine: com.example.timeline.engine.preview.PreviewEngine,
    clipTransform: ClipTransform,
    clipAdjustments: AdjustmentStack,
    playheadFrame: Long = 0L,
    onSwitchEngine: (PreviewEngineType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val renderState = remember(clipTransform, playheadFrame, previewEngine.engineType) {
        PreviewTransformAdapter.adaptTransformForEngine(clipTransform, previewEngine.engineType, playheadFrame)
    }

    val evaluatedTransform = renderState.transform

    val colorMatrixState = remember { androidx.compose.ui.graphics.ColorMatrix() }
    val colorFilterState = remember { androidx.compose.runtime.mutableStateOf(ColorFilter.colorMatrix(colorMatrixState)) }

    // Use SideEffect or just a block here to mutate the existing matrix without creating new ones
    // and only updating what is necessary. But to trigger remeasure, we can just use remember(clipAdjustments)
    // If the engine natively handles color grading and adjustments, do NOT double-apply the basic matrix in Compose!
    val shouldApplyComposeFilter = !previewEngine.capabilities.canApplyBasicAdjustments && !previewEngine.capabilities.canApplyRealtimeColorGrade

    val currentColorFilter = remember(clipAdjustments, shouldApplyComposeFilter) {
        if (!shouldApplyComposeFilter) return@remember null
        
        val b = clipAdjustments.params.brightness * 255f
        val c = 1f + clipAdjustments.params.contrast
        val s = 1f + clipAdjustments.params.saturation - 1f
        
        colorMatrixState.values[0] = c; colorMatrixState.values[1] = 0f; colorMatrixState.values[2] = 0f; colorMatrixState.values[3] = 0f; colorMatrixState.values[4] = b
        colorMatrixState.values[5] = 0f; colorMatrixState.values[6] = c; colorMatrixState.values[7] = 0f; colorMatrixState.values[8] = 0f; colorMatrixState.values[9] = b
        colorMatrixState.values[10] = 0f; colorMatrixState.values[11] = 0f; colorMatrixState.values[12] = c; colorMatrixState.values[13] = 0f; colorMatrixState.values[14] = b
        colorMatrixState.values[15] = 0f; colorMatrixState.values[16] = 0f; colorMatrixState.values[17] = 0f; colorMatrixState.values[18] = 1f; colorMatrixState.values[19] = 0f
        
        if (s != 1f) {
            val satMatrix = androidx.compose.ui.graphics.ColorMatrix()
            satMatrix.setToSaturation(s)
            colorMatrixState.timesAssign(satMatrix)
        }
        
        ColorFilter.colorMatrix(colorMatrixState)
    }

    Box(modifier = modifier.fillMaxSize()) {
        ViewportTransformLayer(renderState = renderState, colorFilter = currentColorFilter) {
            when (previewEngine) {
                is com.example.timeline.engine.preview.browser.BrowserPreviewEngine -> {
                    AndroidView(
                        factory = { _ ->
                            val webView = previewEngine.webView
                            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is com.example.timeline.engine.preview.Media3FallbackPreviewEngine -> {
                    AndroidView(
                        factory = { ctx ->
                            val viewMap = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.texture_player_view, null, false) as PlayerView
                            viewMap.apply {
                                this.player = previewEngine.exoPlayer
                                setKeepContentOnPlayerReset(true)
                            }
                        },
                        update = { view ->
                            if (view.player != previewEngine.exoPlayer) {
                                view.player = previewEngine.exoPlayer
                            }
                            view.resizeMode = when (evaluatedTransform.fitMode) {
                                com.example.timeline.core.transform.TransformFitMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                com.example.timeline.core.transform.TransformFitMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                com.example.timeline.core.transform.TransformFitMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                com.example.timeline.core.transform.TransformFitMode.ORIGINAL_SIZE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        onRelease = { view ->
                            view.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is com.example.timeline.engine.preview.native.NativeVlcPreviewEngine -> {
                    AndroidView(
                        factory = { ctx ->
                            org.videolan.libvlc.util.VLCVideoLayout(ctx).apply {
                                previewEngine.attachToVideoLayout(this)
                            }
                        },
                        onRelease = { view ->
                            previewEngine.detachFromVideoLayout()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

