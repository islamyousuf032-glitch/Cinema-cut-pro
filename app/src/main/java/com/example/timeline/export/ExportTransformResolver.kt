package com.example.timeline.export

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineClip

class ExportTransformResolver {

    /**
     * Finds the visible clips for a given frame and their evaluated transforms.
     * Useful if the exporter supports multi-layer compositing.
     */
    fun resolveVisibleClipsWithTransform(project: TimelineProject, frame: Long): List<Pair<TimelineClip, com.example.timeline.core.transform.ClipTransform>> {
        val visibleClips = mutableListOf<Pair<TimelineClip, com.example.timeline.core.transform.ClipTransform>>()
        
        for (track in project.tracks) {
            val clip = track.clips.find { frame >= it.timelineStart && frame < it.timelineEnd }
            if (clip != null) {
                visibleClips.add(Pair(clip, clip.transform.evaluateTransformAtFrame(frame)))
            }
        }
        
        return visibleClips
    }
}
