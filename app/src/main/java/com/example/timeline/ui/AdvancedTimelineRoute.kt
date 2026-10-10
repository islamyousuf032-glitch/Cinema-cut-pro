package com.example.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.editor.EditorViewModel
import com.example.timeline.engine.preview.TimelinePreviewController

@Composable
fun AdvancedTimelineRoute(
    timelineViewModel: TimelineViewModel,
    editorViewModel: EditorViewModel,
    timelinePreviewController: TimelinePreviewController,
    onRelinkRequest: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AdvancedTimelineScreen(
        timelineViewModel = timelineViewModel,
        editorViewModel = editorViewModel,
        timelinePreviewController = timelinePreviewController,
        onRelinkRequest = onRelinkRequest,
        onClose = onClose
    )
}
