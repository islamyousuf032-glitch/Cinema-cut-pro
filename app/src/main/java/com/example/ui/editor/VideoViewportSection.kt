package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeline.ui.TimelineUiState
import com.example.timeline.engine.preview.TimelinePreviewController
import com.example.timeline.ui.TimelineViewModel
import com.example.ui.editor.viewport.*
import com.example.timeline.ui.viewport.CleanVideoCanvas
import com.example.timeline.ui.viewport.ViewportSettingsMenu

@Composable
fun VideoViewportSection(
    timelineUiState: TimelineUiState,
    timelineViewModel: TimelineViewModel,
    editorViewModel: EditorViewModel,
    timelinePreviewController: TimelinePreviewController,
    playheadFrame: Long,
    isPlaying: Boolean,
    isSampling: Boolean,
    onColorSampled: (Float, Float, Float) -> Unit,
    onRelinkRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val project = timelineUiState.project
    val settings = project.settings
    
    val engineType by timelinePreviewController.engineTypeFlow.collectAsState()
    val previewSettings by editorViewModel.previewSettings.collectAsState()
    val currentClip by timelinePreviewController.activeClip.collectAsState()
    val currentAsset by timelinePreviewController.activeAsset.collectAsState()
    val playerState by timelinePreviewController.previewEngine.currentState.collectAsState()
    val playerError by timelinePreviewController.previewEngine.currentError.collectAsState()
    
    var showSafeArea by remember { mutableStateOf(false) }
    var showGuideOverlay by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(project) {
        timelinePreviewController.onProjectChanged(project)
    }

    LaunchedEffect(previewSettings.autoGenerateProxy, project.mediaAssets) {
        if (previewSettings.autoGenerateProxy) {
            project.mediaAssets
                .filter { it.proxyStatus == com.example.timeline.media.ProxyStatus.RECOMMENDED }
                .forEach { asset -> timelineViewModel.generateProxy(asset.assetId) }
        }
    }

    LaunchedEffect(playheadFrame) {
        if (!isPlaying) {
            timelinePreviewController.seekToTimelineFrame(project, playheadFrame)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            timelinePreviewController.play()
        } else {
            timelinePreviewController.pause()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                timelinePreviewController.updatePlayheadFromPlayer(
                    onUpdateTimelineFrame = { posMs, tlStart, srcIn ->
                        timelineViewModel.syncPlayheadFromPlayer(posMs, tlStart, srcIn)
                    },
                    onPlaybackEnded = {
                        timelineViewModel.pausePlay()
                    }
                )
                kotlinx.coroutines.delay(16)
            }
        }
    }

    LaunchedEffect(currentClip, playerState) {
        if (playerError != null && currentAsset != null) {
            timelineViewModel.markAssetPlaybackFailed(currentAsset!!.assetId)
        }
    }

    val aspectRatioFloat = settings.resolutionWidth.toFloat() / settings.resolutionHeight.toFloat()

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            
            // 2. ViewportTitleRow (optional)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${project.name} | ${settings.resolutionWidth}x${settings.resolutionHeight} | ${settings.getFpsRational()}fps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth
                val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                val availableHeight = if (maxHeight != androidx.compose.ui.unit.Dp.Infinity && maxHeight > 0.dp) maxHeight else screenHeight
                // Leave room for the viewport title, transport controls and spacing. In landscape,
                // the parent viewport can be shorter than the old hard-coded 180dp minimum.
                val canvasMaxHeight = (availableHeight - 72.dp).coerceAtLeast(64.dp)
                    .coerceAtMost(420.dp)

                val (canvasWidth, canvasHeight) = if (aspectRatioFloat > 1.0f) {
                    val calculatedHeight = availableWidth / aspectRatioFloat
                    val h = minOf(calculatedHeight, canvasMaxHeight, 280.dp)
                    val w = minOf(availableWidth, h * aspectRatioFloat)
                    Pair(w, h)
                } else if (aspectRatioFloat < 1.0f) {
                    val h = minOf(420.dp, availableHeight * 0.38f, canvasMaxHeight)
                    val w = h * aspectRatioFloat
                    Pair(w, h)
                } else {
                    val size = minOf(availableWidth, 320.dp, canvasMaxHeight)
                    Pair(size, size)
                }

                CleanVideoCanvas(
                    controller = timelinePreviewController,
                    project = project,
                    currentAsset = currentAsset,
                    currentClip = currentClip,
                    playheadFrame = playheadFrame,
                    playerState = playerState,
                    playerError = playerError,
                    isSamplingColor = isSampling,
                    onColorSampled = onColorSampled,
                    timelineViewModel = timelineViewModel,
                    showSafeArea = showSafeArea,
                    showGuideOverlay = showGuideOverlay,
                    modifier = Modifier.size(canvasWidth, canvasHeight)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            ViewportControlBar(
                timelineViewModel = timelineViewModel,
                isPlaying = isPlaying,
                onTogglePlay = { timelineViewModel.togglePlay() },
                showSafeArea = showSafeArea,
                onShowSafeAreaChanged = { showSafeArea = it },
                showGuideOverlay = showGuideOverlay,
                onShowGuideOverlayChanged = { showGuideOverlay = it },
                timecodeText = com.example.timeline.core.FrameTimecode.frameToTimecode(playheadFrame, settings.getFpsRational().toFloat().toInt()),
                onSettingsClick = { showSettingsMenu = true }
            )
            // ViewportStatusCard (appears only when there's an error)
            ViewportStatusCard(
                currentAsset = currentAsset,
                playerError = playerError,
                engineType = engineType.displayName,
                onRequestProxy = { timelineViewModel.generateProxy(it) },
                onRequestRelink = onRelinkRequest,
                onRetryPlayback = { 
                    timelinePreviewController.loadProject(project)
                    timelinePreviewController.seekToTimelineFrame(project, playheadFrame)
                    if (isPlaying) timelinePreviewController.play()
                },
                onSwitchEngine = { type ->
                    timelinePreviewController.setEngineType(type)
                    editorViewModel.updateSelectedEngine(type)
                }
            )
        }

        // Settings Menu Overlay
        if (showSettingsMenu) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showSettingsMenu = false }) {
                ViewportSettingsMenu(
                    activeEngine = engineType,
                    capabilities = timelinePreviewController.previewEngine.capabilities,
                    settings = previewSettings,
                    onSettingsChanged = { newSettings ->
                        editorViewModel.updateSelectedEngine(newSettings.selectedEngine)
                        editorViewModel.updateQualityMode(newSettings.qualityMode)
                        editorViewModel.updatePreviewMode(newSettings.previewMode)
                        if (newSettings.showDebugOverlay != previewSettings.showDebugOverlay) {
                            editorViewModel.toggleDebugOverlay()
                        }
                        if (newSettings.showEngineBadge != previewSettings.showEngineBadge) {
                            editorViewModel.toggleEngineBadge()
                        }
                        if (newSettings.autoFallbackOnFailure != previewSettings.autoFallbackOnFailure) {
                            editorViewModel.toggleAutoFallback()
                        }
                        if (newSettings.autoGenerateProxy != previewSettings.autoGenerateProxy) {
                            editorViewModel.toggleAutoGenerateProxy()
                        }
                    },
                    onEngineSelected = { type ->
                        timelinePreviewController.setEngineType(type)
                        editorViewModel.updateSelectedEngine(type)
                        showSettingsMenu = false
                    },
                    onDismiss = { showSettingsMenu = false }
                )
            }
        }
        
        if (previewSettings.showDebugOverlay) {
            DeveloperDebugPanel(
                currentAsset = currentAsset,
                playheadFrame = playheadFrame,
                engineType = engineType.displayName
            )
        }
    }
}
