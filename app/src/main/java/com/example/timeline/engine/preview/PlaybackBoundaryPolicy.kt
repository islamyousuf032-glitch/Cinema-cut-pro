package com.example.timeline.engine.preview

import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject

internal sealed interface PlaybackBoundaryDecision {
    data class AdvanceToNextClip(val frame: Long, val clip: TimelineClip) : PlaybackBoundaryDecision
    data class StopAtLastFrame(val frame: Long) : PlaybackBoundaryDecision
}

internal fun playbackBoundaryDecision(
    project: TimelineProject,
    clip: TimelineClip
): PlaybackBoundaryDecision {
    val boundaryFrame = clip.timelineEnd
    val nextClip = TimelineFrameResolver.getTopmostVisibleVideoClip(project, boundaryFrame)
    return if (nextClip == null) {
        PlaybackBoundaryDecision.StopAtLastFrame(
            frame = (boundaryFrame - 1L).coerceAtLeast(clip.timelineStart)
        )
    } else {
        PlaybackBoundaryDecision.AdvanceToNextClip(frame = boundaryFrame, clip = nextClip)
    }
}
