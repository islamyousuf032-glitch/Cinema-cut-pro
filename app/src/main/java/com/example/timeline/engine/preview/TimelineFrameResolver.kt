package com.example.timeline.engine.preview

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TrackType
import com.example.timeline.core.ClipType

object TimelineFrameResolver {

    /**
     * Finds the topmost visible video clip at the given project frame.
     * Ignore track lock state for preview.
     * Ignore disabled clips.
     * Ignore hidden tracks.
     */
    fun getTopmostVisibleVideoClip(project: TimelineProject, frame: Long): TimelineClip? {
        return getTopmostVisibleVideoClipWithIndex(project, frame)?.first
    }

    /**
     * Finds the topmost visible video clip and its track index at the given project frame.
     */
    fun getTopmostVisibleVideoClipWithIndex(project: TimelineProject, frame: Long): Pair<TimelineClip, Int>? {
        val reversedIndexedTracks = project.tracks.withIndex()
            .filter { (_, track) -> track.type == TrackType.VIDEO && track.isVisible }
            .reversed()

        for ((index, track) in reversedIndexedTracks) {
            val clip = track.clips.find {
                it.isEnabled && it.type != ClipType.ADJUSTMENT && frame >= it.timelineStart && frame < it.timelineEnd
            }
            if (clip != null) {
                return Pair(clip, index)
            }
        }
        return null
    }

    /**
     * Retrieves adjustment layers applicable at the current frame, stacked above the specified track index.
     */
    fun getAdjustmentLayersAtFrame(project: TimelineProject, frame: Long, aboveTrackIndex: Int): List<TimelineClip> {
        val adjustments = mutableListOf<TimelineClip>()
        // Tracks are ordered bottom to top, find applicable visible adjustment layers above the video track
        val applicableTracks = project.tracks.withIndex().filter { (index, track) ->
            (track.type == TrackType.ADJUSTMENT || track.clips.any { it.type == ClipType.ADJUSTMENT }) &&
            track.isVisible &&
            index > aboveTrackIndex
        }

        for ((_, track) in applicableTracks) {
            val clip = track.clips.find {
                it.isEnabled && it.type == ClipType.ADJUSTMENT && frame >= it.timelineStart && frame < it.timelineEnd
            }
            if (clip != null) {
                adjustments.add(clip)
            }
        }
        return adjustments
    }

    /**
     * Calculates the exact source frame corresponding to the current project frame.
     */
    fun resolveSourceFrame(projectPlayheadFrame: Long, clip: TimelineClip): Long {
        val clipOffsetProjectFrames = projectPlayheadFrame - clip.timelineStart
        return clip.sourceIn + clipOffsetProjectFrames
    }

    /**
     * Converts a generic frame count to microseconds based on the given fps rational.
     */
    fun convertFrameToMicroseconds(frame: Long, fpsNumerator: Int, fpsDenominator: Int): Long {
        if (fpsNumerator == 0) return 0L
        val fps = fpsNumerator.toDouble() / fpsDenominator.toDouble()
        return (frame.toDouble() / fps * 1_000_000.0).toLong()
    }
}
