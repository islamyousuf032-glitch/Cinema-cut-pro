package com.example.timeline.core

object CoreMarkerEngine {

    fun addProjectMarker(project: TimelineProject, marker: TimelineMarker): TimelineProject {
        return project.copy(markers = project.markers + marker)
    }

    fun updateProjectMarker(project: TimelineProject, marker: TimelineMarker): TimelineProject {
        return project.copy(markers = project.markers.map { if (it.id == marker.id) marker else it })
    }

    fun deleteProjectMarker(project: TimelineProject, markerId: String): TimelineProject {
        return project.copy(markers = project.markers.filter { it.id != markerId })
    }
    
    fun moveProjectMarker(project: TimelineProject, markerId: String, newFrame: Long): TimelineProject {
        return project.copy(markers = project.markers.map { 
            if (it.id == markerId) it.copy(frame = newFrame) else it 
        })
    }

    fun getProjectMarkersInRange(project: TimelineProject, startFrame: Long, endFrame: Long): List<TimelineMarker> {
        return project.markers.filter { it.frame in startFrame..endFrame }.sortedBy { it.frame }
    }
    
    fun jumpToNextProjectMarker(project: TimelineProject, currentFrame: Long): Long? {
        return project.markers.filter { it.frame > currentFrame }.minByOrNull { it.frame }?.frame
    }

    fun jumpToPreviousProjectMarker(project: TimelineProject, currentFrame: Long): Long? {
        return project.markers.filter { it.frame < currentFrame }.maxByOrNull { it.frame }?.frame
    }

    // Similar functions can be built for Track and Clip if needed,
    // but typically users interact with project markers.
    // For completeness, here is addTrackMarker:
    fun addTrackMarker(project: TimelineProject, trackId: String, marker: TimelineMarker): TimelineProject {
        val newTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                track.copy(markers = track.markers + marker)
            } else track
        }
        return project.copy(tracks = newTracks)
    }

    fun addClipMarker(project: TimelineProject, clipId: String, marker: TimelineMarker): TimelineProject {
        val newTracks = project.tracks.map { track ->
            val hasClip = track.clips.any { it.id == clipId }
            if (hasClip) {
                val newClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(markers = clip.markers + marker)
                    } else clip
                }
                track.copy(clips = newClips)
            } else track
        }
        return project.copy(tracks = newTracks)
    }
}
