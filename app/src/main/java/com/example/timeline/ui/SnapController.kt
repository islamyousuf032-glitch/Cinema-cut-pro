package com.example.timeline.ui

import com.example.timeline.core.TimelineProject

class SnapController {
    fun snapFrame(
        inputFrame: Long,
        project: TimelineProject,
        playheadFrame: Long,
        pixelsPerFrame: Float,
        excludeClipId: String? = null,
        fps: Int = 30
    ): Long {
        var closestFrame = inputFrame
        var minDiff = Long.MAX_VALUE

        val thresholdPx = 10f
        val thresholdFrames = (thresholdPx / pixelsPerFrame).toLong().coerceAtLeast(1)

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

        // Add grid targets (1 second intervals)
        val gridStart = Math.max(0, inputFrame - thresholdFrames)
        val gridEnd = inputFrame + thresholdFrames
        for (f in gridStart..gridEnd) {
            if (f % fps == 0L) {
                snapTargets.add(f)
            }
        }

        for (target in snapTargets) {
            val diff = Math.abs(inputFrame - target)
            if (diff <= thresholdFrames && diff < minDiff) {
                minDiff = diff
                closestFrame = target
            }
        }

        return closestFrame
    }
}
