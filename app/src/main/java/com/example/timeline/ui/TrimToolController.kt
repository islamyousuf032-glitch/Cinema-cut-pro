package com.example.timeline.ui

import com.example.ui.editor.EditorViewModel

class TrimToolController(
    private val timelineViewModel: TimelineViewModel,
    private val editorViewModel: EditorViewModel
) {
    fun trimSelectedStartToPlayhead() {
        val playhead = editorViewModel.playheadFrame.value
        timelineViewModel.trimSelectedClipStart(playhead)
    }

    fun trimSelectedEndToPlayhead() {
        val playhead = editorViewModel.playheadFrame.value
        timelineViewModel.trimSelectedClipEnd(playhead)
    }
}
