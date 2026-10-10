package com.example.timeline.ui

import androidx.compose.ui.geometry.Offset
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.engine.native.NativeTimelineCore

sealed class TimelineDragAction {
    object None : TimelineDragAction()
    data class Pan(val startScrollX: Float, val startScrollY: Float) : TimelineDragAction()
    object ScrubPlayhead : TimelineDragAction()
    data class MoveClip(val clip: TimelineClip, val startOffsetFrames: Long) : TimelineDragAction()
    data class TrimStart(val clip: TimelineClip, val originalStart: Long) : TimelineDragAction()
    data class TrimEnd(val clip: TimelineClip, val originalEnd: Long) : TimelineDragAction()
}

object TimelineGestureController {
    fun hitTest(
        x: Float, 
        y: Float, 
        project: TimelineProject, 
        pixelsPerFrame: Float,
        trackHeight: Float = 80f,
        trackPadding: Float = 12f
    ): TimelineClip? {
        if (NativeTimelineCore.isAvailable()) {
            val clips = mutableListOf<Float>()
            project.tracks.forEachIndexed { trackIndex, track ->
                track.clips.forEach { clip ->
                    val left = NativeTimelineCore.frameToX(clip.timelineStart, pixelsPerFrame)
                    val right = NativeTimelineCore.frameToX(clip.timelineEnd, pixelsPerFrame)
                    val top = trackIndex * (trackHeight + trackPadding)
                    clips.addAll(listOf(trackIndex.toFloat(), left, top, right, top + trackHeight))
                }
            }
            
            val hitResult = NativeTimelineCore.nativeHitTestClip(x, y, clips.toFloatArray())
            if (hitResult.hit) {
                var currentIdx = 0
                project.tracks.forEach { track ->
                    track.clips.forEach { clip ->
                        if (currentIdx == hitResult.clipIndex) {
                            return clip
                        }
                        currentIdx++
                    }
                }
            }
        } else {
            project.tracks.forEachIndexed { trackIndex, track ->
                val top = trackIndex * (trackHeight + trackPadding)
                val bottom = top + trackHeight
                track.clips.forEach { clip ->
                    val left = NativeTimelineCore.frameToX(clip.timelineStart, pixelsPerFrame)
                    val right = NativeTimelineCore.frameToX(clip.timelineEnd, pixelsPerFrame)
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
        selectedClipId: String?
    ): TimelineDragAction {
        if (startOffset.y < rulerHeightPx) {
            return TimelineDragAction.ScrubPlayhead
        }

        val tapX = startOffset.x + scrollX
        val tapY = startOffset.y + scrollY - rulerHeightPx
        
        val hitClip = hitTest(tapX, tapY, project, pixelsPerFrame)
        if (hitClip != null) { 
            // If they grabbed an unselected clip, we could select it. But typically trim/move handles are active on selected.
            // For now, allow move if hit any clip, but trim only if near edge.
            val left = NativeTimelineCore.frameToX(hitClip.timelineStart, pixelsPerFrame)
            val right = NativeTimelineCore.frameToX(hitClip.timelineEnd, pixelsPerFrame)
            val trimThreshold = 40f // pixels, slightly larger for touch
            
            if (hitClip.id == selectedClipId && tapX - left < trimThreshold) {
                return TimelineDragAction.TrimStart(hitClip, hitClip.timelineStart)
            } else if (hitClip.id == selectedClipId && right - tapX < trimThreshold) {
                return TimelineDragAction.TrimEnd(hitClip, hitClip.timelineEnd)
            } else {
                val startFrame = NativeTimelineCore.xToFrame(tapX, pixelsPerFrame)
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
        if (!NativeTimelineCore.isAvailable()) return inputFrame
        
        val targetFrames = mutableListOf<Long>()
        val targetTypes = mutableListOf<Int>()
        
        targetFrames.add(playheadFrame)
        targetTypes.add(0) // playhead
        
        project.tracks.forEach { track ->
            track.clips.forEach { clip ->
                if (clip.id != excludeClipId) {
                    targetFrames.add(clip.timelineStart)
                    targetTypes.add(1) // clip edge
                    targetFrames.add(clip.timelineEnd)
                    targetTypes.add(1)
                }
            }
        }
        
        val snapResult = NativeTimelineCore.nativeSnapFrame(
            inputFrame,
            targetFrames.toLongArray(),
            targetTypes.toIntArray(),
            pixelsPerFrame,
            snapThresholdPixels = 15f
        )
        
        return if (snapResult.snapped) snapResult.snappedFrame else inputFrame
    }
}
