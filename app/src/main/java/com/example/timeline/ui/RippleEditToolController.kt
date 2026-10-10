package com.example.timeline.ui

import com.example.timeline.core.RippleDeleteCommand
import com.example.timeline.core.RippleTrimCommand
import com.example.ui.editor.EditorViewModel

class RippleEditToolController(
    private val timelineViewModel: TimelineViewModel,
    private val editorViewModel: EditorViewModel
) {
    fun rippleDeleteSelected(rippleAllTracks: Boolean) {
        val selectedId = timelineViewModel.uiState.value.selectedClipId ?: return
        timelineViewModel.executeCommand(RippleDeleteCommand(listOf(selectedId), rippleAllTracks))
        timelineViewModel.selectClip(null)
    }

    fun rippleTrimStart(clipId: String, newStartFrame: Long, rippleAllTracks: Boolean) {
        timelineViewModel.executeCommand(RippleTrimCommand(clipId, newStartFrame, isStart = true, rippleAllTracks = rippleAllTracks))
    }

    fun rippleTrimEnd(clipId: String, newEndFrame: Long, rippleAllTracks: Boolean) {
        timelineViewModel.executeCommand(RippleTrimCommand(clipId, newEndFrame, isStart = false, rippleAllTracks = rippleAllTracks))
    }
}
