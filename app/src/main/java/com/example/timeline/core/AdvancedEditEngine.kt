package com.example.timeline.core

enum class AffectedTracksMode {
    SAME_TRACK_ONLY,
    ALL_UNLOCKED_TRACKS
}

data class EditResult(
    val isSuccess: Boolean,
    val project: TimelineProject,
    val changedClipIds: List<String> = emptyList(),
    val error: String? = null
)

object AdvancedEditEngine {

    fun findPreviousClipOnTrack(clip: TimelineClip, track: TimelineTrack): TimelineClip? {
        return track.clips.filter { it.timelineEnd <= clip.timelineStart && it.id != clip.id }
            .maxByOrNull { it.timelineEnd }
    }

    fun findNextClipOnTrack(clip: TimelineClip, track: TimelineTrack): TimelineClip? {
        return track.clips.filter { it.timelineStart >= clip.timelineEnd && it.id != clip.id }
            .minByOrNull { it.timelineStart }
    }

    fun areClipsAdjacent(left: TimelineClip, right: TimelineClip): Boolean {
        return left.timelineEnd == right.timelineStart && left.trackId == right.trackId
    }

    fun calculateAvailableSourceHandleLeft(clip: TimelineClip): Long {
        return clip.sourceIn
    }

    fun calculateAvailableSourceHandleRight(clip: TimelineClip, mediaMaxFrames: Long = Long.MAX_VALUE): Long {
        // Since we don't store media duration currently, we assume infinite or use a provided limit
        return Math.max(0, mediaMaxFrames - clip.sourceOut)
    }

    fun shiftClipsAfterFrame(
        project: TimelineProject,
        frame: Long,
        deltaFrames: Long,
        mode: AffectedTracksMode,
        triggerTrackId: String
    ): TimelineResult {
        if (deltaFrames == 0L) return TimelineResult(project)
        
        var newTracks = project.tracks.toList()
        for (trackIndex in newTracks.indices) {
            val track = newTracks[trackIndex]
            if (track.isLocked) continue

            if (mode == AffectedTracksMode.SAME_TRACK_ONLY && track.id != triggerTrackId) continue

            val newClips = track.clips.map { clip ->
                if (clip.timelineStart >= frame) {
                    val newTarget = Math.max(0, clip.timelineStart + deltaFrames)
                    clip.copy(timelineStart = newTarget)
                } else {
                    clip
                }
            }
            newTracks = newTracks.toMutableList().apply {
                this[trackIndex] = track.copy(clips = newClips.sortedBy { it.timelineStart })
            }
        }
        
        return TimelineResult(project.copy(tracks = newTracks))
    }
    
    // 1. Ripple Trim Start
    fun rippleTrimStart(project: TimelineProject, clipId: String, newStartFrame: Long, affectedTracksMode: AffectedTracksMode): EditResult {
        val pair = findTrackAndClip(project, clipId) ?: return EditResult(false, project, error = "Clip not found")
        val (track, clip) = pair
        
        if (track.isLocked || clip.isLocked) return EditResult(false, project, error = "Track or clip is locked")
        
        val deltaFrames = newStartFrame - clip.timelineStart // positive means shortening from left
        if (deltaFrames == 0L) return EditResult(true, project)
        
        val newSourceIn = clip.sourceIn + deltaFrames
        if (newSourceIn < 0) return EditResult(false, project, error = "Cannot trim before source media start")
        if (newSourceIn >= clip.sourceOut) return EditResult(false, project, error = "Cannot trim beyond clip end")

        // The edit boundary is the start of the clip.
        // During ripple trim IN, the clip duration changes and downstream clips (including this one) 
        // shift left/right to close/open the gap. Consequentially, timelineStart of THIS clip stays the same!
        
        val shiftDelta = -deltaFrames 
        val rippleFrame = clip.timelineStart
        
        val modifiedClip = clip.copy(
            timelineStart = newStartFrame, 
            sourceIn = newSourceIn
        )
        
        val tempProject = replaceClip(project, modifiedClip)
        val changedIds = mutableListOf(clipId)
        
        val shiftedTracks = tempProject.tracks.map { trk ->
            if (trk.isLocked) trk else {
                if (affectedTracksMode == AffectedTracksMode.SAME_TRACK_ONLY && trk.id != track.id) trk
                else {
                    val nClips = trk.clips.map { c ->
                        if (c.id == clip.id) {
                            c.copy(timelineStart = Math.max(0, c.timelineStart + shiftDelta))
                        } else if (c.timelineStart >= rippleFrame) {
                            changedIds.add(c.id)
                            c.copy(timelineStart = Math.max(0, c.timelineStart + shiftDelta))
                        } else c
                    }
                    trk.copy(clips = nClips.sortedBy { it.timelineStart })
                }
            }
        }
        
        val finalProject = tempProject.copy(tracks = shiftedTracks)
        if (TimelineValidator.validateTrack(finalProject.tracks.first { it.id == track.id }).isNotEmpty()) {
             return EditResult(false, project, error = "Ripple causes invalid overlap")
        }
        
        return EditResult(true, finalProject, changedIds.distinct())
    }

    // 1b. Ripple Trim End
    fun rippleTrimEnd(project: TimelineProject, clipId: String, newEndFrame: Long, affectedTracksMode: AffectedTracksMode): EditResult {
        val pair = findTrackAndClip(project, clipId) ?: return EditResult(false, project, error = "Clip not found")
        val (track, clip) = pair
        
        if (track.isLocked || clip.isLocked) return EditResult(false, project, error = "Track or clip is locked")
        
        val deltaFrames = newEndFrame - clip.timelineEnd
        if (deltaFrames == 0L) return EditResult(true, project)
        
        val newSourceOut = clip.sourceOut + deltaFrames
        if (newSourceOut <= clip.sourceIn) return EditResult(false, project, error = "Cannot trim before clip start")

        val modifiedClip = clip.copy(sourceOut = newSourceOut)
        val tempProject = replaceClip(project, modifiedClip)
        val changedIds = mutableListOf(clipId)
        
        val rippleFrame = clip.timelineEnd
        val shiftDelta = deltaFrames
        
        val shiftedTracks = tempProject.tracks.map { trk ->
            if (trk.isLocked) trk else {
                if (affectedTracksMode == AffectedTracksMode.SAME_TRACK_ONLY && trk.id != track.id) trk
                else {
                    val nClips = trk.clips.map { c ->
                        if (c.id != clipId && c.timelineStart >= rippleFrame) {
                            changedIds.add(c.id)
                            c.copy(timelineStart = Math.max(0, c.timelineStart + shiftDelta))
                        } else c
                    }
                    trk.copy(clips = nClips.sortedBy { it.timelineStart })
                }
            }
        }
        
        val finalProject = tempProject.copy(tracks = shiftedTracks)
        if (TimelineValidator.validateTrack(finalProject.tracks.first { it.id == track.id }).isNotEmpty()) {
             return EditResult(false, project, error = "Ripple causes invalid overlap")
        }
        
        return EditResult(true, finalProject, changedIds.distinct())
    }

    // 2. Roll Edit
    fun rollEdit(project: TimelineProject, leftClipId: String, rightClipId: String, newCutFrame: Long): EditResult {
        val leftPair = findTrackAndClip(project, leftClipId) ?: return EditResult(false, project, error = "Left clip not found")
        val rightPair = findTrackAndClip(project, rightClipId) ?: return EditResult(false, project, error = "Right clip not found")
        
        if (leftPair.first.id != rightPair.first.id) return EditResult(false, project, error = "Clips not on same track")
        val track = leftPair.first
        val leftClip = leftPair.second
        val rightClip = rightPair.second
        
        if (track.isLocked || leftClip.isLocked || rightClip.isLocked) return EditResult(false, project, error = "Track or clip is locked")
        if (!areClipsAdjacent(leftClip, rightClip)) return EditResult(false, project, error = "Clips are not adjacent")
        
        val originalCutFrame = leftClip.timelineEnd
        val deltaFrames = newCutFrame - originalCutFrame
        if (deltaFrames == 0L) return EditResult(true, project)
        
        val newLeftSourceOut = leftClip.sourceOut + deltaFrames
        if (newLeftSourceOut <= leftClip.sourceIn) return EditResult(false, project, error = "Left clip shrinks to zero or negative")
        
        val newRightTimelineStart = newCutFrame
        val newRightSourceIn = rightClip.sourceIn + deltaFrames
        if (newRightSourceIn >= rightClip.sourceOut) return EditResult(false, project, error = "Right clip shrinks to zero or negative")
        if (newRightSourceIn < 0) return EditResult(false, project, error = "Right clip source in cannot be negative")

        val newLeft = leftClip.copy(sourceOut = newLeftSourceOut)
        val newRight = rightClip.copy(timelineStart = newRightTimelineStart, sourceIn = newRightSourceIn)
        
        val newClips = track.clips.map { c ->
            when (c.id) {
                leftClipId -> newLeft
                rightClipId -> newRight
                else -> c
            }
        }
        val newTracks = project.tracks.map { if (it.id == track.id) track.copy(clips = newClips) else it }
        val finalProject = project.copy(tracks = newTracks)
        
        if (TimelineValidator.validateTrack(finalProject.tracks.first { it.id == track.id }).isNotEmpty()) {
            return EditResult(false, project, error = "Roll causes invalid overlap")
        }
        return EditResult(true, finalProject, listOf(leftClipId, rightClipId))
    }

    // 3. Slip Tool
    fun slipClip(project: TimelineProject, clipId: String, sourceDeltaFrames: Long): EditResult {
        val pair = findTrackAndClip(project, clipId) ?: return EditResult(false, project, error = "Clip not found")
        val (track, clip) = pair
        
        if (track.isLocked || clip.isLocked) return EditResult(false, project, error = "Track or clip is locked")
        if (sourceDeltaFrames == 0L) return EditResult(true, project)
        
        val newSourceIn = clip.sourceIn + sourceDeltaFrames
        val newSourceOut = clip.sourceOut + sourceDeltaFrames
        
        if (newSourceIn < 0) return EditResult(false, project, error = "Slip exceeds left source handle")
        
        val newClip = clip.copy(sourceIn = newSourceIn, sourceOut = newSourceOut)
        val finalProject = replaceClip(project, newClip)
        
        return EditResult(true, finalProject, listOf(clipId))
    }

    // 4. Slide Tool
    fun slideClip(project: TimelineProject, clipId: String, newTimelineStartFrame: Long): EditResult {
        val pair = findTrackAndClip(project, clipId) ?: return EditResult(false, project, error = "Clip not found")
        val (track, clip) = pair
        
        if (track.isLocked || clip.isLocked) return EditResult(false, project, error = "Track or clip is locked")
        
        val deltaFrames = newTimelineStartFrame - clip.timelineStart
        if (deltaFrames == 0L) return EditResult(true, project)
        
        val prevClip = findPreviousClipOnTrack(clip, track)
        val nextClip = findNextClipOnTrack(clip, track)
        
        if (prevClip == null || nextClip == null) {
             return EditResult(false, project, error = "Slide tool requires adjacent clips on both sides")
        }
        if (!areClipsAdjacent(prevClip, clip)) return EditResult(false, project, error = "Slide tool requires left adjacent clip")
        if (!areClipsAdjacent(clip, nextClip)) return EditResult(false, project, error = "Slide tool requires right adjacent clip")
        if (prevClip.isLocked || nextClip.isLocked) return EditResult(false, project, error = "Adjacent clips are locked")
        
        val newPrevSourceOut = prevClip.sourceOut + deltaFrames
        val newNextSourceIn = nextClip.sourceIn + deltaFrames
        
        if (newPrevSourceOut <= prevClip.sourceIn) return EditResult(false, project, error = "Previous clip has insufficient duration to slide left")
        if (newNextSourceIn < 0) return EditResult(false, project, error = "Next clip has insufficient left handle to extend left")
        if (newNextSourceIn >= nextClip.sourceOut) return EditResult(false, project, error = "Next clip has insufficient duration to slide right")
        
        val newPrev = prevClip.copy(sourceOut = newPrevSourceOut)
        val newClip = clip.copy(timelineStart = newTimelineStartFrame)
        val newNext = nextClip.copy(timelineStart = newClip.timelineEnd, sourceIn = newNextSourceIn)
        
        val newClips = track.clips.map { c ->
            when (c.id) {
                prevClip.id -> newPrev
                clip.id -> newClip
                nextClip.id -> newNext
                else -> c
            }
        }
        val newTracks = project.tracks.map { if (it.id == track.id) track.copy(clips = newClips) else it }
        val finalProject = project.copy(tracks = newTracks)
        
        if (TimelineValidator.validateTrack(finalProject.tracks.first { it.id == track.id }).isNotEmpty()) {
            return EditResult(false, project, error = "Slide causes invalid overlap")
        }
        
        return EditResult(true, finalProject, listOf(prevClip.id, clip.id, nextClip.id))
    }

    private fun findTrackAndClip(project: TimelineProject, clipId: String): Pair<TimelineTrack, TimelineClip>? {
        for (track in project.tracks) {
            val clip = track.clips.find { it.id == clipId }
            if (clip != null) return Pair(track, clip)
        }
        return null
    }
    
    private fun replaceClip(project: TimelineProject, newClip: TimelineClip): TimelineProject {
        val newTracks = project.tracks.map { track ->
            if (track.id == newClip.trackId) {
                val newClips = track.clips.map { if (it.id == newClip.id) newClip else it }
                track.copy(clips = newClips)
            } else track
        }
        return project.copy(tracks = newTracks)
    }
}
