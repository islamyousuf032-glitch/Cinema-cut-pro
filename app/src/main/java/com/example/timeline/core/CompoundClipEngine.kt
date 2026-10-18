package com.example.timeline.core

import java.util.UUID

object CompoundClipEngine {

    fun compoundClipFromSelection(
        project: TimelineProject,
        selectedClipIds: List<String>,
        newSequenceName: String,
        destTrackId: String
    ): TimelineResult {
        if (selectedClipIds.isEmpty()) return TimelineResult(project, "No clips selected")
        
        val selectedClipsWithTrack = project.tracks.flatMap { track ->
            track.clips.filter { it.id in selectedClipIds }.map { it to track }
        }
        
        if (selectedClipsWithTrack.isEmpty()) return TimelineResult(project, "Clips not found")
        if (selectedClipsWithTrack.any { it.second.isLocked || it.first.isLocked }) {
            return TimelineResult(project, "Cannot compound locked clips/tracks")
        }
        
        val earliestStart = selectedClipsWithTrack.minOf { it.first.timelineStart }
        val latestEnd = selectedClipsWithTrack.maxOf { it.first.timelineEnd }
        val sequenceDuration = latestEnd - earliestStart
        
        val trackMap = mutableMapOf<String, TimelineTrack>()
        val newTracks = mutableListOf<TimelineTrack>()
        
        for ((clip, originalTrack) in selectedClipsWithTrack) {
            trackMap.getOrPut(originalTrack.id) {
                val t = TimelineTrack(name = originalTrack.name, type = originalTrack.type)
                newTracks.add(t)
                t
            }
        }
        
        val finalTracks = newTracks.map { track ->
            val origTrackId = trackMap.entries.first { it.value.id == track.id }.key
            val clipsForTrack = selectedClipsWithTrack.filter { it.second.id == origTrackId }.map { it.first }
            
            val nestedClips = clipsForTrack.map { 
                it.copy(timelineStart = it.timelineStart - earliestStart, trackId = track.id) 
            }
            track.copy(clips = nestedClips.sortedBy { it.timelineStart })
        }
        
        val newSequence = TimelineSequence(
            settings = project.settings.copy(projectName = newSequenceName),
            tracks = finalTracks
        )
        
        var currentProject = project
        val deleteRes = CoreEditEngine.deleteClips(currentProject, selectedClipIds, rippleMode = false)
        if (!deleteRes.isSuccess) return TimelineResult(project, deleteRes.error)
        currentProject = deleteRes.project
        
        currentProject = NestedTimelineEngine.createSequence(currentProject, newSequence)
        
        val nestedClip = TimelineClip(
            trackId = destTrackId,
            mediaId = newSequence.id,
            name = newSequenceName,
            sourceIn = 0,
            sourceOut = sequenceDuration,
            timelineStart = earliestStart,
            type = ClipType.NESTED_SEQUENCE
        )
        
        return CoreTimelineEngine.addClip(currentProject, destTrackId, nestedClip)
    }

    fun decomposeCompoundClip(
        project: TimelineProject,
        compoundClipId: String
    ): TimelineResult {
        val pair = project.tracks.flatMap { track ->
            track.clips.filter { it.id == compoundClipId }.map { track to it }
        }.firstOrNull() ?: return TimelineResult(project, "Compound clip not found")
        
        val (parentTrack, compoundClip) = pair
        
        if (parentTrack.isLocked || compoundClip.isLocked) {
             return TimelineResult(project, "Track or clip is locked")
        }
        if (compoundClip.type != ClipType.NESTED_SEQUENCE) {
             return TimelineResult(project, "Clip is not a nested sequence")
        }
        
        val sequence = project.sequences.find { it.id == compoundClip.mediaId }
            ?: return TimelineResult(project, "Sequence reference not found")
            
        var currentProject = CoreEditEngine.deleteClips(project, listOf(compoundClipId), rippleMode = false).project
        
        for (seqTrack in sequence.tracks) {
            val newTrack = TimelineTrack(
                name = "${sequence.name} - ${seqTrack.name}",
                type = seqTrack.type
            )
            val addTrackRes = CoreTimelineEngine.addTrack(currentProject, newTrack)
            currentProject = addTrackRes.project
            
            val addedTrackId = currentProject.tracks.last().id
            
            for (seqClip in seqTrack.clips) {
                 val seqClipStart = seqClip.timelineStart
                 val seqClipEnd = seqClip.timelineEnd
                 
                 if (seqClipEnd <= compoundClip.sourceIn || seqClipStart >= compoundClip.sourceOut) continue
                 
                 val visibleStartInSeq = Math.max(seqClipStart, compoundClip.sourceIn)
                 val visibleEndInSeq = Math.min(seqClipEnd, compoundClip.sourceOut)
                 
                 val trimStartDelta = visibleStartInSeq - seqClipStart
                 val trimEndDelta = seqClipEnd - visibleEndInSeq
                 
                 val newSourceIn = seqClip.sourceIn + trimStartDelta
                 val newSourceOut = seqClip.sourceOut - trimEndDelta
                 
                 val timelineStartOffset = visibleStartInSeq - compoundClip.sourceIn
                 val finalTimelineStart = compoundClip.timelineStart + timelineStartOffset
                 
                 val decompClip = seqClip.copy(
                     id = UUID.randomUUID().toString(),
                     trackId = addedTrackId,
                     sourceIn = newSourceIn,
                     sourceOut = newSourceOut,
                     timelineStart = finalTimelineStart
                 )
                 
                 val addRes = CoreTimelineEngine.addClip(currentProject, addedTrackId, decompClip)
                 if (!addRes.isSuccess) {
                     return TimelineResult(project, "Failed to place decomposed clip: ${addRes.error}")
                 }
                 currentProject = addRes.project
            }
        }
        
        return TimelineResult(currentProject)
    }
}
