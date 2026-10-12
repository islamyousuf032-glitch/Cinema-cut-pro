package com.example.timeline.engine.edit

import com.example.timeline.core.*
import com.example.timeline.media.MediaAsset
import java.util.UUID

object AddToTimelineController {

    fun determineTargetTrack(project: TimelineProject, mediaAsset: MediaAsset): TimelineTrack {
        val targetType = if (mediaAsset.mimeType.startsWith("audio/")) TrackType.AUDIO else TrackType.VIDEO
        val unlockedTrack = project.tracks.firstOrNull { it.type == targetType && !it.isLocked }
        if (unlockedTrack != null) {
            return unlockedTrack
        }
        val fallbackTrack = project.tracks.firstOrNull { it.type == targetType }
        if (fallbackTrack != null) {
            return fallbackTrack
        }
        // Should create a new track, but for now just return the very first track if possible (fallback)
        return project.tracks.firstOrNull() ?: throw IllegalStateException("No tracks available")
    }

    fun findEmptySpace(track: TimelineTrack, desiredStart: Long, duration: Long): Long {
        var currentStart = desiredStart
        while (true) {
            val overlappingClip = track.clips.find { clip ->
                currentStart < clip.timelineEnd && (currentStart + duration) > clip.timelineStart
            }
            if (overlappingClip == null) {
                break
            }
            currentStart = overlappingClip.timelineEnd
        }
        return currentStart
    }

    /** The first inserted timeline item is anchored at frame zero, regardless of a stale playhead. */
    internal fun resolveStartFrame(
        project: TimelineProject,
        targetTrack: TimelineTrack,
        playheadFrame: Long,
        duration: Long
    ): Long {
        // Audio-only clips do not establish the video sequence start. The first video clip
        // must begin at frame zero even if the project already contains an audio bed.
        val hasExistingVideoClips = project.tracks.any { track ->
            track.type == TrackType.VIDEO && track.isVisible && track.clips.any { it.isEnabled }
        }
        val desiredStart = if (hasExistingVideoClips) playheadFrame.coerceAtLeast(0L) else 0L
        return findEmptySpace(targetTrack, desiredStart, duration)
    }

    fun createAddClipCommand(project: TimelineProject, mediaAsset: MediaAsset, playheadFrame: Long, mode: String = "active", selectedTrackId: String? = null): Pair<TimelineProject, TimelineClip> {
        var p = project
        val hasVideo = mediaAsset.metadata.hasVideo || mediaAsset.mimeType.startsWith("image/")
        val hasAudio = mediaAsset.metadata.hasAudio

        val fpsRational = p.settings.getFpsRational()
        val durationProjectFrames = if (mediaAsset.metadata.durationUs > 0) {
            // Convert us to frames: frames = (durationUs * fpsNumerator) / (1,000,000 * fpsDenominator)
            val computed = (mediaAsset.metadata.durationUs * fpsRational.numerator) / (1_000_000L * fpsRational.denominator)
            if (computed > 0) computed else 150L
        } else {
            if (mediaAsset.metadata.durationFramesInSourceRate > 0) mediaAsset.metadata.durationFramesInSourceRate else 150L
        }

        var primaryClip: TimelineClip? = null
        
        if (hasVideo) {
            var targetTrack: TimelineTrack? = null
            if (mode == "active") {
                val hasExistingEnabledVisibleVideo = p.tracks.any { track ->
                    track.type == TrackType.VIDEO && track.isVisible && track.clips.any { it.isEnabled }
                }
                targetTrack = p.tracks.firstOrNull { track ->
                    track.type == TrackType.VIDEO && track.isVisible && !track.isLocked &&
                        (if (hasExistingEnabledVisibleVideo) track.clips.any { it.isEnabled } else track.clips.isEmpty())
                }
            }
            if (targetTrack == null) {
                targetTrack = TimelineTrack(
                    id = UUID.randomUUID().toString(),
                    name = "Video ${p.tracks.count { it.type == TrackType.VIDEO } + 1}",
                    type = TrackType.VIDEO,
                    height = 100
                )
                p = p.copy(tracks = p.tracks + targetTrack)
            }
            
            
            val startFrame = resolveStartFrame(p, targetTrack, playheadFrame, durationProjectFrames)
            primaryClip = TimelineClip(
                id = UUID.randomUUID().toString(),
                trackId = targetTrack.id,
                mediaId = mediaAsset.assetId,
                name = mediaAsset.displayName,
                sourceIn = 0,
                sourceOut = durationProjectFrames,
                timelineStart = startFrame,
                type = ClipType.MEDIA
            )
            p = CoreTimelineEngine.addClip(p, targetTrack.id, primaryClip).project
        }
        
        if (hasAudio && !hasVideo) {
            var targetTrack: TimelineTrack? = null
            if (mode == "active") {
                targetTrack = p.tracks.firstOrNull { it.type == TrackType.AUDIO && !it.isLocked }
            }
            if (targetTrack == null) {
                targetTrack = TimelineTrack(
                    id = UUID.randomUUID().toString(),
                    name = "Audio ${p.tracks.count { it.type == TrackType.AUDIO } + 1}",
                    type = TrackType.AUDIO,
                    height = 80
                )
                p = p.copy(tracks = p.tracks + targetTrack)
            }
            
            // If primaryClip (video) exists, sync the audio clip's start frame to match it
            val startFrame = primaryClip?.timelineStart ?: resolveStartFrame(p, targetTrack, playheadFrame, durationProjectFrames)
            val audioClip = TimelineClip(
                id = UUID.randomUUID().toString(),
                trackId = targetTrack.id,
                mediaId = mediaAsset.assetId,
                name = mediaAsset.displayName + " (Audio)",
                sourceIn = 0,
                sourceOut = durationProjectFrames,
                timelineStart = startFrame,
                type = ClipType.MEDIA
            )
            p = CoreTimelineEngine.addClip(p, targetTrack.id, audioClip).project
            if (primaryClip == null) {
                primaryClip = audioClip
            }
        }
        
        if (primaryClip == null) {
            // Fallback for unknown media, assume video behavior
            var targetTrack = p.tracks.firstOrNull { it.type == TrackType.VIDEO && !it.isLocked } ?: p.tracks.firstOrNull { it.type == TrackType.VIDEO }
            if (targetTrack == null) {
                targetTrack = TimelineTrack(
                    id = UUID.randomUUID().toString(),
                    name = "Video ${p.tracks.count { it.type == TrackType.VIDEO } + 1}",
                    type = TrackType.VIDEO,
                    height = 100
                )
                p = p.copy(tracks = p.tracks + targetTrack)
            }
            
            
            val startFrame = resolveStartFrame(p, targetTrack, playheadFrame, durationProjectFrames)
            primaryClip = TimelineClip(
                id = UUID.randomUUID().toString(),
                trackId = targetTrack.id,
                mediaId = mediaAsset.assetId,
                name = mediaAsset.displayName,
                sourceIn = 0,
                sourceOut = durationProjectFrames,
                timelineStart = startFrame,
                type = ClipType.MEDIA
            )
            p = CoreTimelineEngine.addClip(p, targetTrack.id, primaryClip).project
        }

        return Pair(p, primaryClip!!)
    }
}
