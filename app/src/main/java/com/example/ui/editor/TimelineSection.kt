package com.example.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.timeline.ui.TimelineViewModel

@Composable
fun TimelineSection(
    timelineViewModel: TimelineViewModel,
    editorViewModel: EditorViewModel,
    launchers: com.example.timeline.ui.MediaImportLaunchers,
    onRelinkRequest: (String) -> Unit
) {
    val timelineUiState by timelineViewModel.uiState.collectAsState()
    val proxyProgress by timelineViewModel.proxyProgress.collectAsState()

    var showMediaSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        com.example.timeline.ui.TimelineScreen(
            viewModel = timelineViewModel,
            editorViewModel = editorViewModel,
            onImportClick = { showMediaSheet = true }
        )
    }
    
    if (showMediaSheet) {
        com.example.ui.editor.MediaLibrarySheet(
            mediaAssets = timelineUiState.project.mediaAssets,
            proxyProgressMap = proxyProgress,
            launchers = launchers,
            timelineViewModel = timelineViewModel,
            onRelinkRequest = onRelinkRequest,
            onDismiss = { showMediaSheet = false }
        )
    }
}
