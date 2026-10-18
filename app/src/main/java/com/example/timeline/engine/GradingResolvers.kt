package com.example.timeline.engine

import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.engine.preview.TimelineFrameResolver

object AdjustmentLayerResolver {
    fun resolveLayers(project: TimelineProject, frame: Long, aboveTrackIndex: Int): List<TimelineClip> {
        return TimelineFrameResolver.getAdjustmentLayersAtFrame(project, frame, aboveTrackIndex)
    }
}

object GradingFrameResolver {
    fun resolve(project: TimelineProject, frame: Long): Pair<TimelineClip, Int>? {
        return TimelineFrameResolver.getTopmostVisibleVideoClipWithIndex(project, frame)
    }
}
