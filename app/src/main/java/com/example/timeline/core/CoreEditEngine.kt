package com.example.timeline.core

import java.util.UUID

enum class TrimMode {
    NORMAL,
    RIPPLE,
    RIPPLE_ALL_TRACKS
}

data class SplitResult(
    val project: TimelineProject,
    val newClipIds: List<String>,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}

object CoreEditEngine {

    private fun findClipAndTrack(project: TimelineProject, clipId: String): Pair<TimelineTrack, TimelineClip>? {
        for (track in project.tracks) {
            val clip = track.clips.find { it.id == clipId }
            if (clip != null) return Pair(track, clip)
        }
        return null
    }

    /**
     * Trims the start of a clip.
     * In NORMAL mode: Modifies timelineStart, sourceIn, and duration. May cause a gap or overlap error.
     * In RIPPLE mode: Eliminates the gap created by the trim. The designated clip remains at its
     * original timeline position, but its source is shortened. Subsequent clips are shifted left.
     * If the clip is lengthened, subsequent clips are shifted right.
     */
    fun trimClipStart(project: TimelineProject, clipId: String, newTimelineStartFrame: Long, mode: TrimMode = TrimMode.NORMAL): TimelineResult {
        val pair = findClipAndTrack(project, clipId) ?: return TimelineResult(project, "Clip not found")
        val (track, clip) = pair

        if (track.isLocked) return TimelineResult(project, "Track is locked")
        if (clip.isLocked) return TimelineResult(project, "Clip is locked")

        val deltaFrames = newTimelineStartFrame - clip.timelineStart
        if (deltaFrames == 0L) return TimelineResult(project)

        val newSourceIn = clip.sourceIn - deltaFrames

        if (newSourceIn < 0) return TimelineResult(project, "Cannot trim before source media start")
        if (newSourceIn >= clip.sourceOut) return TimelineResult(project, "Cannot trim beyond clip end")

        val finalTimelineStart = if (mode == TrimMode.RIPPLE || mode == TrimMode.RIPPLE_ALL_TRACKS) clip.timelineStart else newTimelineStartFrame

        val newClip = clip.copy(
            timelineStart = finalTimelineStart,
            sourceIn = newSourceIn
        )

        val newTracks = project.tracks.map { t ->
            if (t.isLocked) return@map t
            var newClips = t.clips.map { if (it.id == clipId) newClip else it }
            
            if (mode == TrimMode.RIPPLE && t.id == track.id) {
                newClips = newClips.map {
                    if (it.id != clipId && it.timelineStart >= clip.timelineStart) {
                        it.copy(timelineStart = Math.max(0, it.timelineStart - deltaFrames))
                    } else it
                }
            } else if (mode == TrimMode.RIPPLE_ALL_TRACKS) {
                newClips = newClips.map {
                    if (it.id != clipId && it.timelineStart >= clip.timelineStart) {
                        it.copy(timelineStart = Math.max(0, it.timelineStart - deltaFrames))
                    } else it
                }
            }
            t.copy(clips = newClips.sortedBy { it.timelineStart })
        }

        for (t in newTracks) {
            val errors = TimelineValidator.validateTrack(t)
            if (errors.isNotEmpty()) {
                return TimelineResult(project, "Trim overlap: ${errors.first()}")
            }
        }

        return TimelineResult(project.copy(tracks = newTracks))
    }

    /**
     * Trims the end of a clip.
     * In NORMAL mode: Modifies sourceOut and duration.
     * In RIPPLE mode: Also shifts subsequent clips left or right based on the duration change.
     */
    fun trimClipEnd(project: TimelineProject, clipId: String, newTimelineEndFrame: Long, mode: TrimMode = TrimMode.NORMAL): TimelineResult {
        val pair = findClipAndTrack(project, clipId) ?: return TimelineResult(project, "Clip not found")
        val (track, clip) = pair

        if (track.isLocked) return TimelineResult(project, "Track is locked")
        if (clip.isLocked) return TimelineResult(project, "Clip is locked")

        // Positive delta means extending the clip, negative means shortening it.
        val deltaFrames = newTimelineEndFrame - clip.timelineEnd
        if (deltaFrames == 0L) return TimelineResult(project)

        val newSourceOut = clip.sourceOut - deltaFrames

        if (newSourceOut <= clip.sourceIn) return TimelineResult(project, "Cannot trim before clip start")

        val newClip = clip.copy(sourceOut = newSourceOut)
        
        val newTracks = project.tracks.map { t ->
            if (t.isLocked) return@map t
            var newClips = t.clips.map { if (it.id == clipId) newClip else it }
            
            if (mode == TrimMode.RIPPLE && t.id == track.id) {
                newClips = newClips.map {
                    if (it.id != clipId && it.timelineStart >= clip.timelineEnd) {
                        it.copy(timelineStart = Math.max(0, it.timelineStart + deltaFrames))
                    } else it
                }
            } else if (mode == TrimMode.RIPPLE_ALL_TRACKS) {
                newClips = newClips.map {
                    if (it.id != clipId && it.timelineStart >= clip.timelineEnd) {
                        it.copy(timelineStart = Math.max(0, it.timelineStart + deltaFrames))
                    } else it
                }
            }
            t.copy(clips = newClips.sortedBy { it.timelineStart })
        }

        for (t in newTracks) {
            val errors = TimelineValidator.validateTrack(t)
            if (errors.isNotEmpty()) {
                return TimelineResult(project, "Trim overlap: ${errors.first()}")
            }
        }

        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun splitClipAtFrame(project: TimelineProject, clipId: String, splitFrame: Long): SplitResult {
        val pair = findClipAndTrack(project, clipId)
        if (pair == null) return SplitResult(project, emptyList(), "Clip not found")
        val (track, clip) = pair

        if (track.isLocked) return SplitResult(project, emptyList(), "Track is locked")
        if (clip.isLocked) return SplitResult(project, emptyList(), "Clip is locked")

        if (splitFrame <= clip.timelineStart || splitFrame >= clip.timelineEnd) {
            return SplitResult(project, emptyList(), "Split frame must be strictly inside the clip duration")
        }

        val splitOffset = splitFrame - clip.timelineStart
        
        val leftClip = clip.copy(
            sourceOut = clip.sourceIn + splitOffset
        )
        val rightClip = clip.copy(
            id = UUID.randomUUID().toString(),
            timelineStart = splitFrame,
            sourceIn = clip.sourceIn + splitOffset
        )

        val newClips = track.clips.filter { it.id != clipId } + leftClip + rightClip
        val trackForValidation = track.copy(clips = newClips.sortedBy { it.timelineStart })
        
        val newTracks = project.tracks.map { if (it.id == track.id) trackForValidation else it }
        return SplitResult(project.copy(tracks = newTracks), listOf(rightClip.id))
    }

    fun cutUnlockedClipsAtFrame(project: TimelineProject, frame: Long): SplitResult {
        var currentProject = project
        val newClipIds = mutableListOf<String>()
        var errorCount = 0

        for (track in project.tracks) {
            if (track.isLocked) continue
            
            // Identifying clips crossing the playhead
            val intersectingClips = track.clips.filter { clip ->
                frame > clip.timelineStart && frame < clip.timelineEnd && !clip.isLocked && clip.isEnabled
            }

            for (clip in intersectingClips) {
                val splitRes = splitClipAtFrame(currentProject, clip.id, frame)
                if (splitRes.isSuccess) {
                    currentProject = splitRes.project
                    newClipIds.addAll(splitRes.newClipIds)
                } else {
                    errorCount++
                }
            }
        }

        if (errorCount > 0 && newClipIds.isEmpty()) {
            return SplitResult(currentProject, newClipIds, "Failed to cut one or more clips")
        }
        
        return SplitResult(currentProject, newClipIds)
    }

    fun deleteClips(project: TimelineProject, clipIds: List<String>, rippleMode: Boolean = false, rippleAllTracks: Boolean = false): TimelineResult {
        if (clipIds.isEmpty()) return TimelineResult(project)

        val newTracks = project.tracks.map { track ->
            if (track.isLocked) return@map track
            
            val clipsToDelete = project.tracks.flatMap { it.clips }.filter { it.id in clipIds }
            if (clipsToDelete.isEmpty()) return@map track

            var currentClips = track.clips.filter { it.id !in clipIds }
            
            val trackHasDeletions = track.clips.any { it.id in clipIds }

            if (rippleMode && trackHasDeletions && !rippleAllTracks) {
                val trackDeletions = track.clips.filter { it.id in clipIds }
                for (deletedClip in trackDeletions.sortedByDescending { it.timelineStart }) {
                    val shiftAmount = deletedClip.duration
                    currentClips = currentClips.map {
                        if (it.timelineStart >= deletedClip.timelineStart) {
                            it.copy(timelineStart = Math.max(0, it.timelineStart - shiftAmount))
                        } else it
                    }
                }
            } else if (rippleAllTracks) {
                for (deletedClip in clipsToDelete.sortedByDescending { it.timelineStart }) {
                    val shiftAmount = deletedClip.duration
                    currentClips = currentClips.map {
                        if (it.timelineStart >= deletedClip.timelineStart) {
                            it.copy(timelineStart = Math.max(0, it.timelineStart - shiftAmount))
                        } else it
                    }
                }
            }

            track.copy(clips = currentClips.sortedBy { it.timelineStart })
        }
        return TimelineResult(project.copy(tracks = newTracks))
    }
    fun rollEdit(project: TimelineProject, leftClipId: String, rightClipId: String, deltaFrames: Long): TimelineResult {
        if (deltaFrames == 0L) return TimelineResult(project)
        
        val leftPair = findClipAndTrack(project, leftClipId) ?: return TimelineResult(project, "Left clip not found")
        val rightPair = findClipAndTrack(project, rightClipId) ?: return TimelineResult(project, "Right clip not found")
        
        val (track, leftClip) = leftPair
        val (_, rightClip) = rightPair
        
        if (track.id != rightPair.first.id) return TimelineResult(project, "Clips are on different tracks")
        if (track.isLocked) return TimelineResult(project, "Track is locked")
        if (leftClip.isLocked || rightClip.isLocked) return TimelineResult(project, "Clip is locked")
        
        if (leftClip.timelineEnd != rightClip.timelineStart) {
            return TimelineResult(project, "Clips are not strictly adjacent")
        }
        
        val newLeftOut = leftClip.sourceOut + deltaFrames
        val newRightIn = rightClip.sourceIn + deltaFrames
        val newRightTimelineStart = rightClip.timelineStart + deltaFrames
        
        if (newLeftOut <= leftClip.sourceIn) return TimelineResult(project, "Cannot trim left clip before start")
        if (newRightIn >= rightClip.sourceOut) return TimelineResult(project, "Cannot trim right clip beyond end")
        if (newRightTimelineStart <= leftClip.timelineStart) return TimelineResult(project, "Cannot roll beyond left clip start")
        
        val newLeftClip = leftClip.copy(sourceOut = newLeftOut)
        val newRightClip = rightClip.copy(sourceIn = newRightIn, timelineStart = newRightTimelineStart)
        
        val newClips = track.clips.map {
            when (it.id) {
                leftClipId -> newLeftClip
                rightClipId -> newRightClip
                else -> it
            }
        }.sortedBy { it.timelineStart }
        
        val trackForValidation = track.copy(clips = newClips)
        val errors = TimelineValidator.validateTrack(trackForValidation)
        if (errors.isNotEmpty()) {
            return TimelineResult(project, "Roll edit validation failed: ${errors.first()}")
        }
        
        val newTracks = project.tracks.map { if (it.id == track.id) trackForValidation else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun slipEdit(project: TimelineProject, clipId: String, deltaFrames: Long): TimelineResult {
        if (deltaFrames == 0L) return TimelineResult(project)
        
        val pair = findClipAndTrack(project, clipId) ?: return TimelineResult(project, "Clip not found")
        val (track, clip) = pair
        
        if (track.isLocked) return TimelineResult(project, "Track is locked")
        if (clip.isLocked) return TimelineResult(project, "Clip is locked")
        
        val newSourceIn = clip.sourceIn - deltaFrames
        val newSourceOut = clip.sourceOut - deltaFrames
        
        if (newSourceIn < 0) return TimelineResult(project, "Cannot slip before source media start")
        // Note: we don't know the absolute max duration of the source media here if we don't have access to it, 
        // but typically a clip has bounds. We'll just check newSourceIn >= 0.
        // If there's a max limit, we'd need MediaAsset, but CoreEditEngine might not have it.
        // We will assume valid if newSourceIn >= 0 for now.
        
        val newClip = clip.copy(sourceIn = newSourceIn, sourceOut = newSourceOut)
        
        val newClips = track.clips.map { if (it.id == clipId) newClip else it }
        
        val trackForValidation = track.copy(clips = newClips)
        val errors = TimelineValidator.validateTrack(trackForValidation)
        if (errors.isNotEmpty()) {
            return TimelineResult(project, "Slip validation failed: ${errors.first()}")
        }
        
        val newTracks = project.tracks.map { if (it.id == track.id) trackForValidation else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun slideEdit(project: TimelineProject, clipId: String, deltaFrames: Long): TimelineResult {
        if (deltaFrames == 0L) return TimelineResult(project)
        
        val pair = findClipAndTrack(project, clipId) ?: return TimelineResult(project, "Clip not found")
        val (track, clip) = pair
        
        if (track.isLocked) return TimelineResult(project, "Track is locked")
        if (clip.isLocked) return TimelineResult(project, "Clip is locked")
        
        // Find adjacent left and right clips
        val clips = track.clips.sortedBy { it.timelineStart }
        val idx = clips.indexOfFirst { it.id == clipId }
        
        val leftClip = if (idx > 0) clips[idx - 1] else null
        val rightClip = if (idx < clips.size - 1) clips[idx + 1] else null
        
        if (leftClip != null && leftClip.isLocked) return TimelineResult(project, "Left clip is locked")
        if (rightClip != null && rightClip.isLocked) return TimelineResult(project, "Right clip is locked")

        // Strictly adjacent?
        val strictlyAdjacentLeft = leftClip != null && leftClip.timelineEnd == clip.timelineStart
        val strictlyAdjacentRight = rightClip != null && rightClip.timelineStart == clip.timelineEnd

        var newLeftClip = leftClip
        var newRightClip = rightClip
        
        val newTimelineStart = clip.timelineStart + deltaFrames
        val newTimelineEnd = clip.timelineEnd + deltaFrames
        if (newTimelineStart < 0) return TimelineResult(project, "Cannot slide before timeline start")

        if (strictlyAdjacentLeft && leftClip != null) {
            val newLeftOut = leftClip.sourceOut + deltaFrames
            if (newLeftOut <= leftClip.sourceIn) return TimelineResult(project, "Cannot slide beyond left clip's source start")
            newLeftClip = leftClip.copy(sourceOut = newLeftOut)
        } else if (leftClip != null) {
            // Check collision
            if (newTimelineStart < leftClip.timelineEnd) return TimelineResult(project, "Collision with left clip")
        }

        if (strictlyAdjacentRight && rightClip != null) {
            val newRightIn = rightClip.sourceIn + deltaFrames
            val newRightTimelineStart = rightClip.timelineStart + deltaFrames
            if (newRightIn >= rightClip.sourceOut) return TimelineResult(project, "Cannot slide beyond right clip's source end")
            if (newRightIn < 0) return TimelineResult(project, "Cannot slide before right clip's source start")
            newRightClip = rightClip.copy(sourceIn = newRightIn, timelineStart = newRightTimelineStart)
        } else if (rightClip != null) {
            // Check collision
            if (newTimelineEnd > rightClip.timelineStart) return TimelineResult(project, "Collision with right clip")
        }
        
        val newClip = clip.copy(timelineStart = newTimelineStart)
        
        val newClips = track.clips.map {
            when (it.id) {
                clipId -> newClip
                leftClip?.id -> newLeftClip ?: it
                rightClip?.id -> newRightClip ?: it
                else -> it
            }
        }.sortedBy { it.timelineStart }
        
        val trackForValidation = track.copy(clips = newClips)
        val errors = TimelineValidator.validateTrack(trackForValidation)
        if (errors.isNotEmpty()) {
            return TimelineResult(project, "Slide validation failed: ${errors.first()}")
        }
        
        val newTracks = project.tracks.map { if (it.id == track.id) trackForValidation else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

}