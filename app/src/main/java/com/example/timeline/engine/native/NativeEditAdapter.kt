package com.example.timeline.engine.native

import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack

object NativeEditAdapter {

    private fun packClips(clips: List<TimelineClip>, idMap: Map<String, Int>): LongArray {
        val result = LongArray(clips.size * 6)
        for (i in clips.indices) {
            val clip = clips[i]
            val mappedId = idMap[clip.id] ?: 0
            result[i * 6 + 0] = mappedId.toLong()
            result[i * 6 + 1] = clip.timelineStart
            result[i * 6 + 2] = clip.sourceOut - clip.sourceIn
            result[i * 6 + 3] = clip.sourceIn
            result[i * 6 + 4] = clip.sourceOut
            result[i * 6 + 5] = -1L // maxSourceDuration not always available, defaulting to -1 (unlimited)
        }
        return result
    }

    private fun unpackClips(
        originalClips: List<TimelineClip>,
        nativeOutputs: LongArray,
        reverseMap: Map<Int, String>
    ): List<TimelineClip> {
        val updatedClips = mutableListOf<TimelineClip>()
        val numClips = nativeOutputs.size / 5
        for (i in 0 until numClips) {
            val mappedId = nativeOutputs[i * 5 + 0].toInt()
            val startFrame = nativeOutputs[i * 5 + 1]
            val durationFrames = nativeOutputs[i * 5 + 2]
            val sourceIn = nativeOutputs[i * 5 + 3]
            val sourceOut = nativeOutputs[i * 5 + 4]

            val originalId = reverseMap[mappedId]
            val originalClip = originalClips.find { it.id == originalId }
            if (originalClip != null) {
                updatedClips.add(
                    originalClip.copy(
                        timelineStart = startFrame,
                        sourceIn = sourceIn,
                        sourceOut = sourceOut
                    )
                )
            }
        }
        return updatedClips
    }

    private fun applyToProject(
        project: TimelineProject,
        trackId: String,
        updatedClips: List<TimelineClip>
    ): TimelineProject {
        val newTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                track.copy(clips = updatedClips.sortedBy { it.timelineStart })
            } else {
                track
            }
        }
        return project.copy(tracks = newTracks)
    }

    fun rippleEdit(project: TimelineProject, clipId: String, isStart: Boolean, deltaFrames: Long): TimelineProject {
        if (!NativeTimelineCore.isAvailable()) return project

        var targetTrack: TimelineTrack? = null
        for (track in project.tracks) {
            if (track.clips.any { it.id == clipId }) {
                targetTrack = track
                break
            }
        }
        if (targetTrack == null) return project

        val idMap = targetTrack.clips.withIndex().associate { it.value.id to it.index }
        val reverseMap = idMap.entries.associate { (k, v) -> v to k }
        
        val inputData = packClips(targetTrack.clips, idMap)
        val targetMappedId = idMap[clipId] ?: return project
        
        val outputData = NativeTimelineCore.nativeRippleEdit(inputData, targetMappedId, isStart, deltaFrames)
        
        val updatedClips = unpackClips(targetTrack.clips, outputData, reverseMap)
        return applyToProject(project, targetTrack.id, updatedClips)
    }

    fun rollEdit(project: TimelineProject, leftClipId: String, rightClipId: String, deltaFrames: Long): TimelineProject {
        if (!NativeTimelineCore.isAvailable()) return project

        var targetTrack: TimelineTrack? = null
        for (track in project.tracks) {
            if (track.clips.any { it.id == leftClipId }) {
                targetTrack = track
                break
            }
        }
        if (targetTrack == null) return project

        val idMap = targetTrack.clips.withIndex().associate { it.value.id to it.index }
        val reverseMap = idMap.entries.associate { (k, v) -> v to k }
        
        val inputData = packClips(targetTrack.clips, idMap)
        val leftMappedId = idMap[leftClipId] ?: return project
        val rightMappedId = idMap[rightClipId] ?: return project
        
        val outputData = NativeTimelineCore.nativeRollEdit(inputData, leftMappedId, rightMappedId, deltaFrames)
        
        val updatedClips = unpackClips(targetTrack.clips, outputData, reverseMap)
        return applyToProject(project, targetTrack.id, updatedClips)
    }

    fun slipEdit(project: TimelineProject, clipId: String, deltaFrames: Long): TimelineProject {
        if (!NativeTimelineCore.isAvailable()) return project

        var targetTrack: TimelineTrack? = null
        var targetClip: TimelineClip? = null
        for (track in project.tracks) {
            targetClip = track.clips.find { it.id == clipId }
            if (targetClip != null) {
                targetTrack = track
                break
            }
        }
        if (targetTrack == null || targetClip == null) return project

        val idMap = mapOf(targetClip.id to 0)
        val reverseMap = mapOf(0 to targetClip.id)
        
        val inputData = packClips(listOf(targetClip), idMap)
        
        val outputData = NativeTimelineCore.nativeSlipEdit(inputData, deltaFrames)
        
        val updatedClips = unpackClips(listOf(targetClip), outputData, reverseMap)
        
        val allClips = targetTrack.clips.map { if (it.id == clipId) updatedClips.first() else it }
        return applyToProject(project, targetTrack.id, allClips)
    }

    fun slideEdit(project: TimelineProject, clipId: String, deltaFrames: Long): TimelineProject {
        if (!NativeTimelineCore.isAvailable()) return project

        var targetTrack: TimelineTrack? = null
        for (track in project.tracks) {
            if (track.clips.any { it.id == clipId }) {
                targetTrack = track
                break
            }
        }
        if (targetTrack == null) return project

        val idMap = targetTrack.clips.withIndex().associate { it.value.id to it.index }
        val reverseMap = idMap.entries.associate { (k, v) -> v to k }
        
        val inputData = packClips(targetTrack.clips, idMap)
        val targetMappedId = idMap[clipId] ?: return project
        
        val outputData = NativeTimelineCore.nativeSlideEdit(inputData, targetMappedId, deltaFrames)
        
        val updatedClips = unpackClips(targetTrack.clips, outputData, reverseMap)
        return applyToProject(project, targetTrack.id, updatedClips)
    }
}
