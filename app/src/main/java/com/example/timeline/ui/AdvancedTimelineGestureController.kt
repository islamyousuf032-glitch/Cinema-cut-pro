package com.example.timeline.ui

import androidx.compose.ui.geometry.Offset
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.engine.native.advanced.NativeAdvancedTimelineCore

object AdvancedTimelineGestureController {
    fun hitTest(
        x: Float, 
        y: Float, 
        project: TimelineProject, 
        pixelsPerFrame: Float,
        trackHeight: Float = 80f,
        trackPadding: Float = 12f
    ): TimelineClip? {
        if (NativeAdvancedTimelineCore.isAvailable()) {
            val clips = mutableListOf<Float>()
            var currentIdx = 0
            project.tracks.forEachIndexed { trackIndex, track ->
                track.clips.forEach { clip ->
                    val left = NativeAdvancedTimelineCore.frameToX(clip.timelineStart, pixelsPerFrame)
                    val right = NativeAdvancedTimelineCore.frameToX(clip.timelineEnd, pixelsPerFrame)
                    val top = trackIndex * (trackHeight + trackPadding)
                    clips.addAll(listOf(currentIdx.toFloat(), left, top, right, top + trackHeight))
                    currentIdx++
                }
            }
            
            val hitResult = NativeAdvancedTimelineCore.nativeHitTestClip(x, y, clips.toFloatArray())
            if (hitResult.isNotEmpty() && hitResult[0] > 0.5f) {
                val hitIdx = hitResult[1].toInt()
                var idx = 0
                project.tracks.forEach { track ->
                    track.clips.forEach { clip ->
                        if (idx == hitIdx) {
                            return clip
                        }
                        idx++
                    }
                }
            }
        } else {
            project.tracks.forEachIndexed { trackIndex, track ->
                val top = trackIndex * (trackHeight + trackPadding)
                val bottom = top + trackHeight
                track.clips.forEach { clip ->
                    val left = NativeAdvancedTimelineCore.frameToX(clip.timelineStart, pixelsPerFrame)
                    val right = NativeAdvancedTimelineCore.frameToX(clip.timelineEnd, pixelsPerFrame)
                    if (x in left..right && y in top..bottom) {
                        return clip
                    }
                }
            }
        }
        return null
    }

    fun determineDragAction(
        startOffset: Offset,
        scrollX: Float,
        scrollY: Float,
        rulerHeightPx: Float,
        project: TimelineProject,
        pixelsPerFrame: Float,
        selectedClipIds: Set<String>
    ): TimelineDragAction {
        if (startOffset.y < rulerHeightPx) {
            return TimelineDragAction.ScrubPlayhead
        }
        
        val tapX = startOffset.x + scrollX
        val tapY = startOffset.y + scrollY - rulerHeightPx
        
        val hitClip = hitTest(tapX, tapY, project, pixelsPerFrame)
        if (hitClip != null) { 
            val left = NativeAdvancedTimelineCore.frameToX(hitClip.timelineStart, pixelsPerFrame)
            val right = NativeAdvancedTimelineCore.frameToX(hitClip.timelineEnd, pixelsPerFrame)
            val trimThreshold = 40f
            
            if (selectedClipIds.contains(hitClip.id) && tapX - left < trimThreshold) {
                return TimelineDragAction.TrimStart(hitClip, hitClip.timelineStart)
            } else if (selectedClipIds.contains(hitClip.id) && right - tapX < trimThreshold) {
                return TimelineDragAction.TrimEnd(hitClip, hitClip.timelineEnd)
            } else {
                val startFrame = NativeAdvancedTimelineCore.xToFrame(tapX, pixelsPerFrame)
                val offsetFrames = startFrame - hitClip.timelineStart
                return TimelineDragAction.MoveClip(hitClip, offsetFrames)
            }
        }
        
        return TimelineDragAction.Pan(scrollX, scrollY)
    }

    fun snapFrame(
        inputFrame: Long,
        project: TimelineProject,
        playheadFrame: Long,
        pixelsPerFrame: Float,
        excludeClipId: String? = null
    ): Long {
        if (!NativeAdvancedTimelineCore.isAvailable()) return inputFrame
        
        val snapTargets = mutableListOf<Long>()
        snapTargets.add(playheadFrame)
        
        project.tracks.forEach { track ->
            track.clips.forEach { clip ->
                if (clip.id != excludeClipId) {
                    snapTargets.add(clip.timelineStart)
                    snapTargets.add(clip.timelineEnd)
                }
            }
        }
        
        project.markers.forEach { marker ->
            snapTargets.add(marker.frame)
        }
        
        val thresholdPx = 10f
        val thresholdFrames = (thresholdPx / pixelsPerFrame).toLong().coerceAtLeast(1)
        
        val fps = Math.round(project.settings.getFpsRational().toFloat()).toLong()
        val gridStart = Math.max(0, inputFrame - thresholdFrames)
        val gridEnd = inputFrame + thresholdFrames
        for (f in gridStart..gridEnd) {
            if (f % fps == 0L) {
                snapTargets.add(f)
            }
        }

        
        return NativeAdvancedTimelineCore.nativeSnapFrame(
            inputFrame,
            snapTargets.toLongArray(),
            thresholdFrames
        )
    }
}
