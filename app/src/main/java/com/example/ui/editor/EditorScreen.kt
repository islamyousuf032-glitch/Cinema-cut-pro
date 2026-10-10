package com.example.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.timeline.ui.TimelineViewModel
import com.example.ui.ColorAdjustmentViewModel
import com.example.ui.ColorMatchViewModel
import com.example.ui.scopes.ScopeViewModel
import com.example.MainViewModel
import com.example.timeline.engine.preview.TimelinePreviewController
import com.example.ui.EditorTopBar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun EditorScreen(
    timelineViewModel: TimelineViewModel,
    colorAdjustmentViewModel: ColorAdjustmentViewModel,
    colorMatchViewModel: ColorMatchViewModel,
    scopeViewModel: ScopeViewModel,
    mainViewModel: MainViewModel,
    editorViewModel: EditorViewModel,
    timelinePreviewController: TimelinePreviewController,
    onExportClick: () -> Unit,
    onDebugClick: () -> Unit,
    showScopes: Boolean,
    onToggleScopes: () -> Unit,
    launchers: com.example.timeline.ui.MediaImportLaunchers,
    relinkLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, List<android.net.Uri>>,
    onRelinkRequest: (String) -> Unit,
    onOpenAdvancedTimeline: () -> Unit
) {
    val timelineUiState by timelineViewModel.uiState.collectAsState()
    val isPlaying by timelineViewModel.isPlaying.collectAsState()
    val playheadFrame by editorViewModel.playheadFrame.collectAsState()
    val isSampling by colorAdjustmentViewModel.isEyedropperActive.collectAsState()
    val scopeData by scopeViewModel.scopeData.collectAsState()
    val isAnalyzingScopes by scopeViewModel.isAnalyzing.collectAsState()

    val activeClipAndAsset = remember(playheadFrame, timelineUiState.project.mediaAssets) {
        timelineViewModel.getActiveVideoClipAndAsset()
    }
    val isProxyActive = activeClipAndAsset?.second?.proxyStatus == com.example.timeline.media.ProxyStatus.READY

    var selectedTab by remember { mutableStateOf("Timeline") }
    var viewportWeight by remember { mutableFloatStateOf(0.4f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VideoViewportSection(
            timelineUiState = timelineUiState,
            timelineViewModel = timelineViewModel,
            editorViewModel = editorViewModel,
            timelinePreviewController = timelinePreviewController,
            playheadFrame = playheadFrame,
            isPlaying = isPlaying,
            isSampling = isSampling,
            onColorSampled = { r, g, b -> colorAdjustmentViewModel.applyEyedropperColor(r, g, b) },
            onRelinkRequest = onRelinkRequest,
            modifier = Modifier.weight(viewportWeight)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        // delta is roughly pixels, we need to convert to weight roughly
                        // 1000 pixels is 1f weight roughly, so 0.001f per pixel
                        val weightDelta = delta * 0.001f
                        viewportWeight = (viewportWeight + weightDelta).coerceIn(0.2f, 0.8f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
            )
        }

        EditorToolTabs(
            selectedTab = selectedTab,
            onTabSelected = { 
                if (it == "Pro Timeline") {
                    onOpenAdvancedTimeline()
                } else {
                    selectedTab = it 
                }
            }
        )

        EditorBottomPanel(
            selectedTab = selectedTab,
            timelineViewModel = timelineViewModel,
            colorAdjustmentViewModel = colorAdjustmentViewModel,
            colorMatchViewModel = colorMatchViewModel,
            scopeViewModel = scopeViewModel,
            timelinePreviewController = timelinePreviewController,
            mainViewModel = mainViewModel,
            editorViewModel = editorViewModel,
            launchers = launchers,
            onRelinkRequest = onRelinkRequest,
            modifier = Modifier.fillMaxWidth().weight(1f - viewportWeight)
        )
        }
        if (showScopes) {
            com.example.ui.colorgrade.ScopesPanel(
                scopeData = scopeData,
                isAnalyzing = isAnalyzingScopes,
                onClose = onToggleScopes,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            )
        }
    }
}
