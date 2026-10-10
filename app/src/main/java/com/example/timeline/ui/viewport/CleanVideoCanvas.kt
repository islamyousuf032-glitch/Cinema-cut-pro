package com.example.timeline.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.engine.preview.TimelinePreviewController
import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MediaAvailabilityStatus
import com.example.timeline.media.MediaErrorStatus
import com.example.timeline.media.PreviewStatus
import com.example.timeline.media.ProxyStatus
import com.example.timeline.ui.TimelineViewModel
import kotlinx.coroutines.launch

@Composable
fun CleanVideoCanvas(
    controller: TimelinePreviewController,
    project: TimelineProject,
    currentAsset: MediaAsset?,
    currentClip: TimelineClip?,
    playheadFrame: Long,
    playerState: com.example.timeline.engine.preview.PreviewState,
    playerError: String?,
    isSamplingColor: Boolean,
    onColorSampled: (Float, Float, Float) -> Unit,
    timelineViewModel: TimelineViewModel,
    showSafeArea: Boolean,
    showGuideOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var activeOverlayMode by remember { mutableStateOf("BoundingBox") }
    var liveTransformOverride by remember { mutableStateOf<ClipTransform?>(null) }
    val effectiveTransform = liveTransformOverride ?: currentClip?.transform ?: ClipTransform()

    LaunchedEffect(currentClip) {
        liveTransformOverride = null
    }

    val aspectRatioFloat = project.settings.resolutionWidth.toFloat() / project.settings.resolutionHeight.toFloat()
    val projectBgColor = try {
         Color(android.graphics.Color.parseColor(project.settings.canvasBackgroundColor))
    } catch (e: Exception) {
         Color.Black
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isSamplingColor) {
                detectTapGestures { offset ->
                    if (isSamplingColor) {
                        val x = offset.x / size.width.toFloat()
                        val y = offset.y / size.height.toFloat()
                        scope.launch {
                            val bitmap = controller.extractCurrentFrameBitmap()
                            if (bitmap != null) {
                                val px = (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                val py = (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                val pixel = bitmap.getPixel(px, py)
                                val r = android.graphics.Color.red(pixel) / 255f
                                val g = android.graphics.Color.green(pixel) / 255f
                                val b = android.graphics.Color.blue(pixel) / 255f
                                onColorSampled(r, g, b)
                            }
                        }
                    } else {
                        timelineViewModel.togglePlay()
                    }
                }
            }
    ) {
        ProjectCanvas(
            projectBackgroundColor = projectBgColor,
            aspectRatio = aspectRatioFloat,
            modifier = Modifier.fillMaxSize()
        ) {
            if (currentAsset != null && currentClip != null) {
                val isReady = playerState == com.example.timeline.engine.preview.PreviewState.READY || playerState == com.example.timeline.engine.preview.PreviewState.PLAYING
                val needsStillFrame = currentAsset.proxyStatus == ProxyStatus.GENERATING || 
                                      currentAsset.proxyStatus == ProxyStatus.REQUIRED ||
                                      currentAsset.previewStatus == PreviewStatus.DIRECT_FAILED_RUNTIME ||
                                      (!isReady && playerError != null)
                
                val hasProxy = currentAsset.proxyStatus == ProxyStatus.READY
                val isDirectFailed = currentAsset.previewStatus == PreviewStatus.DIRECT_FAILED_RUNTIME || (hasProxy && currentAsset.previewStatus == PreviewStatus.NOT_TESTED)

                if (currentAsset.mediaAvailabilityStatus == MediaAvailabilityStatus.MISSING_NEEDS_RELINK || currentAsset.mediaErrorStatus != MediaErrorStatus.NONE) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                } else if (isDirectFailed && hasProxy) {
                    com.example.timeline.engine.preview.PlayerViewContainer(
                        previewEngine = controller.previewEngine,
                        clipTransform = effectiveTransform,
                        clipAdjustments = currentClip.adjustments,
                        playheadFrame = playheadFrame,
                        onSwitchEngine = { type -> controller.setEngineType(type) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (needsStillFrame) {
                    val sourceFrame = playheadFrame - currentClip.timelineStart + currentClip.sourceIn
                    val fps = project.settings.getFpsRational()
                    val timeUs = (sourceFrame.toDouble() / (fps.numerator.toDouble() / fps.denominator.toDouble()) * 1_000_000.0).toLong()
                    
                    StillFrameFallback(
                        uriString = currentAsset.localOriginalUriString ?: currentAsset.originalUriString, 
                        timeUs = timeUs, 
                        project = project, 
                        projectFrame = playheadFrame, 
                        clipTransform = effectiveTransform, 
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    com.example.timeline.engine.preview.PlayerViewContainer(
                        previewEngine = controller.previewEngine,
                        clipTransform = effectiveTransform,
                        clipAdjustments = currentClip.adjustments,
                        playheadFrame = playheadFrame,
                        onSwitchEngine = { type -> controller.setEngineType(type) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                TransformHandleOverlay(
                    clip = currentClip.copy(transform = effectiveTransform),
                    currentFrame = playheadFrame,
                    timelineViewModel = timelineViewModel,
                    onUpdateClipTransform = { clip, transform -> timelineViewModel.updateClipTransform(clip.id, transform) },
                    onLiveTransformUpdate = { t -> liveTransformOverride = t },
                    modifier = Modifier.fillMaxSize(),
                    activeOverlayMode = activeOverlayMode
                )
                
                MotionPathRenderer(
                    clip = currentClip,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        if (showSafeArea) {
            SafeAreaOverlay()
        }

        if (showGuideOverlay) {
            CanvasGuideOverlay()
        }
    }
}
