package com.example.timeline.ui

import com.example.timeline.core.SplitClipCommand
import com.example.ui.editor.EditorViewModel

class BladeToolController(
    private val timelineViewModel: TimelineViewModel
) {
    fun handleTap(clipId: String?, tapFrame: Long, linkedSelection: Boolean) {
        if (clipId == null) return
        
        val project = timelineViewModel.uiState.value.project
        val track = project.tracks.find { it.clips.any { c -> c.id == clipId } } ?: return
        if (track.isLocked) return
        
        val clip = track.clips.find { it.id == clipId } ?: return
        if (clip.isLocked) return
        
        // Split frame must be strictly inside the clip
        if (tapFrame > clip.timelineStart && tapFrame < clip.timelineEnd) {
            val cmd = SplitClipCommand(
                clipId = clipId, 
                splitFrame = tapFrame, 
                splitLinkedAudio = linkedSelection
            )
            timelineViewModel.executeCommand(cmd)
        }
    }
}
