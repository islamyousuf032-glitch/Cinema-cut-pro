package com.example.timeline.ui

import com.example.timeline.core.SlideCommand
import com.example.ui.editor.EditorViewModel

class SlideToolController(
    private val timelineViewModel: TimelineViewModel,
    private val editorViewModel: EditorViewModel
) {
    fun handleSlide(clipId: String, deltaFrames: Long) {
        timelineViewModel.executeCommand(SlideCommand(clipId, deltaFrames))
    }
}
