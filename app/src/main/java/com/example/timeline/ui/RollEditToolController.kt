package com.example.timeline.ui

import com.example.timeline.core.RollEditCommand
import com.example.ui.editor.EditorViewModel

class RollEditToolController(
    private val timelineViewModel: TimelineViewModel,
    private val editorViewModel: EditorViewModel
) {
    fun handleRoll(clipId: String, diffFrames: Long, isStartDrag: Boolean) {
        val state = timelineViewModel.uiState.value
        val track = state.project.tracks.find { it.clips.any { c -> c.id == clipId } } ?: return
        val clips = track.clips.sortedBy { it.timelineStart }
        val idx = clips.indexOfFirst { it.id == clipId }
        
        val leftClipId: String
        val rightClipId: String
        
        if (isStartDrag) {
            if (idx == 0) return // No clip to the left
            leftClipId = clips[idx - 1].id
            rightClipId = clipId
        } else {
            if (idx == clips.size - 1) return // No clip to the right
            leftClipId = clipId
            rightClipId = clips[idx + 1].id
        }
        
        timelineViewModel.executeCommand(RollEditCommand(leftClipId, rightClipId, diffFrames))
    }
}
