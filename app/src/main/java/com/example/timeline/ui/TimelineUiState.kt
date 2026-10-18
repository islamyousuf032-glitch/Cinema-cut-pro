package com.example.timeline.ui

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineSnapSettings
import com.example.timeline.core.ProjectSettings

data class TimelineUiState(
    val showProjectSetup: Boolean = true,
    val project: TimelineProject = TimelineProject(settings = ProjectSettings("new", "New")),
    val pixelsPerFrame: Float = 2f,
    val selectedClipId: String? = null,
    val selectedClipIds: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val snapSettings: TimelineSnapSettings = TimelineSnapSettings(),
    val showDebugPanel: Boolean = false
)
