package com.example.timeline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timeline.engine.preview.TimelinePreviewController
import com.example.ui.editor.EditorViewModel
import com.example.timeline.core.ActiveProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel

class TimelineViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimelineViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun AdvancedTimelineEntryScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    // We recreate the viewmodels specific for this activity
    val timelineViewModel: TimelineViewModel = viewModel(
        factory = TimelineViewModelFactory(context)
    )
    val editorViewModel: EditorViewModel = viewModel()
    
    val preparationManager = remember { AdvancedTimelinePreparationManager() }
    val loadState by preparationManager.loadState.collectAsState()
    val loadError by preparationManager.error.collectAsState()
    
    val uiState by timelineViewModel.uiState.collectAsState()
    val playheadFrame by timelineViewModel.playheadFrame.collectAsState()
    
    LaunchedEffect(Unit) {
        val activeProject = ActiveProjectManager.activeProject
        if (activeProject != null) {
            timelineViewModel.loadProject(activeProject)
            timelineViewModel.setPlayheadFrame(ActiveProjectManager.playheadFrame)
            editorViewModel.updatePlayheadFrame(ActiveProjectManager.playheadFrame)
            timelineViewModel.selectClip(ActiveProjectManager.selectedClipId)
            
            // Run the detailed preparation steps
            preparationManager.prepare()
        } else {
            // Can't run manager if no project
            // We just let the UI handle this as a failure state directly or let the manager fail
        }
    }
    
    // Sync back to ActiveProjectManager on close/dispose
    DisposableEffect(Unit) {
        onDispose {
            ActiveProjectManager.activeProject = uiState.project
            ActiveProjectManager.playheadFrame = playheadFrame
            ActiveProjectManager.selectedClipId = uiState.selectedClipId
            ActiveProjectManager.isDirty = true
        }
    }
    
    if (loadState != AdvancedTimelineLoadState.READY) {
        AdvancedTimelineLoadingScreen(
            state = loadState,
            error = loadError ?: if (ActiveProjectManager.activeProject == null) "Project not found in memory" else null,
            onClose = onClose
        )
    } else {
        val timelinePreviewController = remember { TimelinePreviewController(context) }
        
        DisposableEffect(timelinePreviewController) {
            timelinePreviewController.onPlayheadAdvanced = { newFrame ->
                editorViewModel.updatePlayheadFrame(newFrame)
                timelineViewModel.setPlayheadFrame(newFrame)
            }
            onDispose { 
                timelinePreviewController.onPlayheadAdvanced = null
                timelinePreviewController.release()
            }
        }
        
        AdvancedTimelineScreen(
            timelineViewModel = timelineViewModel,
            editorViewModel = editorViewModel,
            timelinePreviewController = timelinePreviewController,
            onRelinkRequest = { /* No-op or launch activity */ },
            onClose = onClose
        )
    }
}
