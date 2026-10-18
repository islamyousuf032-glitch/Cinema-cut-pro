package com.example.timeline.ui

data class TimelineSelectionState(
    val selectedClipIds: Set<String> = emptySet(),
    val isDragging: Boolean = false,
    val dragAction: TimelineDragAction = TimelineDragAction.None
) {
    val selectedClipId: String? get() = selectedClipIds.firstOrNull()
}
