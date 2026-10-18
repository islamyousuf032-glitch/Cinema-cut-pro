package com.example.timeline.export.validation

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TrackType

/**
 * Builds an undoable export-oriented timeline repair by compacting enabled clips on the sole
 * visible video track. Media3 Transformer 1.5 cannot produce a video gap, so gaps and overlaps are
 * removed by shifting clips to consecutive frame positions. Original trims and all clip metadata
 * are preserved.
 */
object TimelineExportAutoFixer {
    fun compactVisibleVideoTrack(project: TimelineProject): TimelineProject? {
        val visibleVideoTracks = project.tracks.filter { track ->
            track.type == TrackType.VIDEO && track.isVisible && track.clips.any { it.isEnabled }
        }
        if (visibleVideoTracks.size != 1) return null

        val track = visibleVideoTracks.single()
        if (track.isLocked) return null

        val enabledClips = track.clips
            .filter { it.isEnabled }
            .sortedWith(compareBy({ it.timelineStart }, { it.id }))
        if (enabledClips.isEmpty() || enabledClips.any { it.duration <= 0L }) return null

        var nextStartFrame = 0L
        val movedClips = mutableMapOf<String, com.example.timeline.core.TimelineClip>()
        for (clip in enabledClips) {
            if (clip.timelineStart != nextStartFrame && clip.isLocked) return null
            movedClips[clip.id] = clip.copy(timelineStart = nextStartFrame)
            nextStartFrame = runCatching { Math.addExact(nextStartFrame, clip.duration) }.getOrNull() ?: return null
        }

        if (enabledClips.all { movedClips[it.id]?.timelineStart == it.timelineStart }) return null

        val repairedTrack = track.copy(
            clips = track.clips.map { clip -> movedClips[clip.id] ?: clip }
        )
        return project.copy(
            tracks = project.tracks.map { candidate ->
                if (candidate.id == track.id) repairedTrack else candidate
            }
        )
    }
}
