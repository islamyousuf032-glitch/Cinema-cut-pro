package com.example.timeline.export.resolver

import com.example.timeline.core.TimelineTrack

data class RenderTrackPlan(
    val track: TimelineTrack,
    val zIndex: Int,
    val isVideoTrack: Boolean,
    val isAudioTrack: Boolean,
    val isMuted: Boolean,
    val isHidden: Boolean
)
