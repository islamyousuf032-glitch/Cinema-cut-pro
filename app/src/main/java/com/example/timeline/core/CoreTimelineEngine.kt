package com.example.timeline.core

data class TimelineResult(
    val project: TimelineProject,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}

object CoreTimelineEngine {
    fun addTrack(project: TimelineProject, track: TimelineTrack): TimelineResult {
        return TimelineResult(project.copy(tracks = project.tracks + track))
    }

    fun removeTrack(project: TimelineProject, trackId: String): TimelineResult {
        return TimelineResult(project.copy(tracks = project.tracks.filter { it.id != trackId }))
    }

    fun renameTrack(project: TimelineProject, trackId: String, newName: String): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(name = newName) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun reorderTrack(project: TimelineProject, trackId: String, newIndex: Int): TimelineResult {
        val tracks = project.tracks.toMutableList()
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        if (trackIndex == -1) return TimelineResult(project, "Track not found")
        
        val track = tracks.removeAt(trackIndex)
        val targetIndex = newIndex.coerceIn(0, tracks.size)
        tracks.add(targetIndex, track)
        
        return TimelineResult(project.copy(tracks = tracks))
    }

    fun lockTrack(project: TimelineProject, trackId: String, isLocked: Boolean): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(isLocked = isLocked) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun setTrackMute(project: TimelineProject, trackId: String, isMuted: Boolean): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(isMuted = isMuted) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun setTrackSolo(project: TimelineProject, trackId: String, isSolo: Boolean): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(isSolo = isSolo) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun setTrackEnabled(project: TimelineProject, trackId: String, isEnabled: Boolean): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(isVisible = isEnabled) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }
    
    fun setTrackExpanded(project: TimelineProject, trackId: String, isExpanded: Boolean): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(isExpanded = isExpanded) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun updateTrackHeight(project: TimelineProject, trackId: String, height: Int): TimelineResult {
        val newTracks = project.tracks.map { if (it.id == trackId) it.copy(height = height) else it }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun addClip(project: TimelineProject, trackId: String, clip: TimelineClip): TimelineResult {
        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        if (trackIndex == -1) return TimelineResult(project, "Track not found")
        val track = project.tracks[trackIndex]
        
        if (track.isLocked) return TimelineResult(project, "Cannot add clip to locked track")
        
        val clipErrors = TimelineValidator.validateClip(clip)
        if (clipErrors.isNotEmpty()) return TimelineResult(project, clipErrors.joinToString(", "))
        
        if (TimelineValidator.detectTrackCollision(track, clip)) {
            return TimelineResult(project, "Overlap detected on non-layered track")
        }

        val updatedTrack = track.copy(clips = (track.clips + clip).sortedBy { it.timelineStart })
        val newTracks = project.tracks.toMutableList().apply { this[trackIndex] = updatedTrack }
        return TimelineResult(project.copy(tracks = newTracks))
    }

    fun moveClipToTrack(
        project: TimelineProject, 
        clipId: String, 
        sourceTrackId: String, 
        destTrackId: String, 
        newTimelineStart: Long
    ): TimelineResult {
        val sourceTrackIndex = project.tracks.indexOfFirst { it.id == sourceTrackId }
        val destTrackIndex = project.tracks.indexOfFirst { it.id == destTrackId }
        
        if (sourceTrackIndex == -1 || destTrackIndex == -1) return TimelineResult(project, "Track not found")
        
        val sourceTrack = project.tracks[sourceTrackIndex]
        val destTrack = project.tracks[destTrackIndex]
        
        if (sourceTrack.isLocked) return TimelineResult(project, "Source track is locked")
        if (destTrack.isLocked) return TimelineResult(project, "Destination track is locked")
        
        val clip = sourceTrack.clips.find { it.id == clipId } ?: return TimelineResult(project, "Clip not found")
        
        // Simple type compatibility check: prevent Video <-> Audio moves
        val incompatible = (sourceTrack.type == TrackType.VIDEO && destTrack.type == TrackType.AUDIO) ||
                           (sourceTrack.type == TrackType.AUDIO && destTrack.type == TrackType.VIDEO)
        if (incompatible) {
            return TimelineResult(project, "Incompatible track type")
        }

        val updatedClip = clip.copy(timelineStart = newTimelineStart, trackId = destTrackId)

        // Validate overlap on destination
        // Temporary filter out the clip itself if we are moving within the SAME track
        val destTrackForValidation = if (sourceTrackId == destTrackId) {
            destTrack.copy(clips = destTrack.clips.filter { it.id != clipId })
        } else destTrack

        if (TimelineValidator.detectTrackCollision(destTrackForValidation, updatedClip)) {
             return TimelineResult(project, "Overlap detected on destination track")
        }

        val newSourceClips = sourceTrack.clips.filter { it.id != clipId }
        val newDestClips = if (sourceTrackId == destTrackId) {
            newSourceClips + updatedClip
        } else {
            destTrack.clips + updatedClip
        }.sortedBy { it.timelineStart }

        val newTracks = project.tracks.toMutableList()
        newTracks[sourceTrackIndex] = sourceTrack.copy(clips = newSourceClips)
        newTracks[destTrackIndex] = destTrack.copy(clips = newDestClips)

        return TimelineResult(project.copy(tracks = newTracks))
    }
}
