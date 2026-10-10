package com.example.timeline.core

import kotlin.math.abs
import kotlin.math.roundToLong

object SnapEngine {

    fun collectSnapTargets(
        project: TimelineProject,
        playheadFrame: Long? = null,
        includeGrid: Boolean = false,
        gridIntervalFrames: Long = 24L,
        maxGridFrame: Long = 10000L
    ): List<SnapTarget> {
        val targets = mutableListOf<SnapTarget>()

        if (playheadFrame != null) {
            targets.add(SnapTarget(playheadFrame, SnapTargetType.PLAYHEAD))
        }

        for (track in project.tracks) {
            for (clip in track.clips) {
                targets.add(SnapTarget(clip.timelineStart, SnapTargetType.CLIP_BOUNDARY, clip.id))
                targets.add(SnapTarget(clip.timelineEnd, SnapTargetType.CLIP_BOUNDARY, clip.id))
                
                for (marker in clip.markers) {
                    val absoluteMarkerFrame = clip.timelineStart + marker.frame
                    val type = if (marker.type == MarkerType.BEAT) SnapTargetType.BEAT_MARKER else SnapTargetType.MARKER
                    targets.add(SnapTarget(absoluteMarkerFrame, type, marker.id))
                }
            }
            for (marker in track.markers) {
                val type = if (marker.type == MarkerType.BEAT) SnapTargetType.BEAT_MARKER else SnapTargetType.MARKER
                targets.add(SnapTarget(marker.frame, type, marker.id))
            }
        }
        
        for (marker in project.markers) {
            val type = if (marker.type == MarkerType.BEAT) SnapTargetType.BEAT_MARKER else SnapTargetType.MARKER
            targets.add(SnapTarget(marker.frame, type, marker.id))
        }

        if (includeGrid && gridIntervalFrames > 0) {
            val endFrame = Math.max(maxGridFrame, project.tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEnd } ?: 0L)
            for (f in 0..endFrame step gridIntervalFrames) {
                targets.add(SnapTarget(f, SnapTargetType.GRID))
            }
        }

        return targets
    }

    fun snapFrame(
        project: TimelineProject,
        requestedFrame: Long,
        settings: TimelineSnapSettings,
        zoomFramesPerPixel: Float,
        playheadFrame: Long? = null,
        ignoredClipId: String? = null
    ): SnapResult {
        if (!settings.isSnappingEnabled) {
            return SnapResult(requestedFrame, requestedFrame, null, null, 0L)
        }

        val snapThresholdFrames = (settings.snapThresholdPixels * zoomFramesPerPixel).roundToLong()
        
        val targets = collectSnapTargets(
            project = project,
            playheadFrame = if (settings.snapToPlayhead) playheadFrame else null,
            includeGrid = settings.snapToGrid,
            gridIntervalFrames = settings.gridIntervalFrames
        )
        
        // Filter out targets belonging to ignoredClipId
        val validTargets = targets.filter { target ->
            when (target.type) {
                SnapTargetType.CLIP_BOUNDARY -> settings.snapToClips && target.targetId != ignoredClipId
                SnapTargetType.MARKER, SnapTargetType.BEAT_MARKER -> settings.snapToMarkers && target.targetId != ignoredClipId
                SnapTargetType.PLAYHEAD -> settings.snapToPlayhead
                SnapTargetType.GRID -> settings.snapToGrid
            }
        }

        val inRangeTargets = validTargets.map { target ->
            target to abs(target.frame - requestedFrame)
        }.filter { it.second <= snapThresholdFrames }

        if (inRangeTargets.isEmpty()) {
            return SnapResult(requestedFrame, requestedFrame, null, null, 0L)
        }

        // Sort by distance ascending, then by priority (lower number == higher priority)
        val bestTargetPair = inRangeTargets.sortedWith(compareBy({ it.second }, { it.first.type.priority })).first()
        val bestTarget = bestTargetPair.first
        
        return SnapResult(
            originalFrame = requestedFrame,
            snappedFrame = bestTarget.frame,
            snappedTo = bestTarget.type,
            targetId = bestTarget.targetId,
            distanceFrames = bestTargetPair.second
        )
    }

    fun snapClipMove(
        project: TimelineProject,
        clipId: String,
        requestedStartFrame: Long,
        settings: TimelineSnapSettings,
        zoomFramesPerPixel: Float,
        playheadFrame: Long? = null
    ): SnapResult {
        var requestedStart = Math.max(0, requestedStartFrame)
        val clip = project.tracks.flatMap { it.clips }.find { it.id == clipId }
            ?: return SnapResult(requestedStartFrame, requestedStartFrame, null, null, 0L)
            
        // When moving a clip, we can snap the start OR the end of the clip
        val startSnap = snapFrame(project, requestedStart, settings, zoomFramesPerPixel, playheadFrame, clipId)
        val endSnap = snapFrame(project, requestedStart + clip.duration, settings, zoomFramesPerPixel, playheadFrame, clipId)

        return if (startSnap.snappedTo != null && endSnap.snappedTo != null) {
            if (startSnap.distanceFrames <= endSnap.distanceFrames) startSnap else {
                // Return start frame adjusted by the end snapping
                SnapResult(requestedStart, endSnap.snappedFrame - clip.duration, endSnap.snappedTo, endSnap.targetId, endSnap.distanceFrames)
            }
        } else if (startSnap.snappedTo != null) {
            startSnap
        } else if (endSnap.snappedTo != null) {
            SnapResult(requestedStartFrame, endSnap.snappedFrame - clip.duration, endSnap.snappedTo, endSnap.targetId, endSnap.distanceFrames)
        } else {
            SnapResult(requestedStartFrame, requestedStartFrame, null, null, 0L)
        }
    }

    fun snapTrimBoundary(
        project: TimelineProject,
        clipId: String,
        requestedBoundaryFrame: Long,
        settings: TimelineSnapSettings,
        zoomFramesPerPixel: Float,
        playheadFrame: Long? = null
    ): SnapResult {
        return snapFrame(project, Math.max(0, requestedBoundaryFrame), settings, zoomFramesPerPixel, playheadFrame, clipId)
    }
}
