package com.example.timeline.ui

import com.example.timeline.core.TimelineProject

class TrackControlController(
    private val timelineViewModel: TimelineViewModel
) {
    fun toggleLock(trackId: String, currentLocked: Boolean) {
        timelineViewModel.lockTrack(trackId, !currentLocked)
    }

    fun toggleVisibility(trackId: String, currentVisible: Boolean) {
        timelineViewModel.enableTrack(trackId, !currentVisible)
    }

    fun toggleMute(trackId: String, currentMuted: Boolean) {
        timelineViewModel.muteTrack(trackId, !currentMuted)
    }

    fun toggleSolo(trackId: String, currentSolo: Boolean) {
        timelineViewModel.soloTrack(trackId, !currentSolo)
    }
}
