package com.example.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.timeline.ui.TimelineViewModel
import com.example.ui.ColorAdjustmentViewModel
import com.example.ui.ColorMatchViewModel
import com.example.ui.scopes.ScopeViewModel
import com.example.MainViewModel
import com.example.timeline.engine.preview.TimelinePreviewController

@Composable
fun EditorBottomPanel(
    selectedTab: String,
    timelineViewModel: TimelineViewModel,
    colorAdjustmentViewModel: ColorAdjustmentViewModel,
    colorMatchViewModel: ColorMatchViewModel,
    scopeViewModel: ScopeViewModel,
    timelinePreviewController: TimelinePreviewController,
    mainViewModel: MainViewModel,
    editorViewModel: EditorViewModel,
    launchers: com.example.timeline.ui.MediaImportLaunchers,
    onRelinkRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when (selectedTab) {
            "Timeline" -> {
                TimelineSection(
                    timelineViewModel = timelineViewModel,
                    editorViewModel = editorViewModel,
                    launchers = launchers,
                    onRelinkRequest = onRelinkRequest
                )
            }
            "Color" -> {
                com.example.ui.colorgrade.ProfessionalColorGradingScreen(
                    timelineViewModel, colorMatchViewModel, scopeViewModel, timelinePreviewController
                )
            }
            "Transform" -> {
                com.example.ui.transform.TransformPanel(timelineViewModel)
            }
            "Motion" -> {
                com.example.ui.transform.MotionPanel(timelineViewModel)
            }
            "Graph" -> {
                com.example.ui.transform.GraphEditorScreen(timelineViewModel)
            }
        }
    }
}
