package com.example.timeline.ui

import com.example.timeline.core.SlipCommand
import com.example.ui.editor.EditorViewModel

class SlipToolController(
    private val timelineViewModel: TimelineViewModel,
    private val editorViewModel: EditorViewModel
) {
    fun handleSlip(clipId: String, deltaFrames: Long) {
        timelineViewModel.executeCommand(SlipCommand(clipId, deltaFrames))
    }
}
