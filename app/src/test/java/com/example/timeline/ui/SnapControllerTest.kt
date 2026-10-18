package com.example.timeline.ui

import com.example.timeline.core.ProjectFrameRatePreset
import com.example.timeline.core.ProjectSettings
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapControllerTest {
    private fun project(tracks: List<TimelineTrack> = emptyList()) = TimelineProject(
        id = "project",
        settings = ProjectSettings(
            projectId = "project",
            projectName = "Test project",
            frameRate = ProjectFrameRatePreset.FPS_24
        ),
        tracks = tracks
    )

    @Test
    fun snapsNearTimelineStartToFrameZero() {
        val snapped = SnapController().snapFrame(
            inputFrame = 1L,
            project = project(),
            playheadFrame = 30L,
            pixelsPerFrame = 10f,
            fps = 24
        )

        assertEquals(0L, snapped)
    }

    @Test
    fun snapsNearEitherClipEdge() {
        val clip = TimelineClip(
            id = "clip",
            trackId = "video-track",
            mediaId = "media",
            name = "clip.mp4",
            sourceIn = 0L,
            sourceOut = 100L,
            timelineStart = 50L
        )
        val track = TimelineTrack("video-track", "Video 1", TrackType.VIDEO, clips = listOf(clip))

        val snapped = SnapController().snapFrame(
            inputFrame = 51L,
            project = project(listOf(track)),
            playheadFrame = 200L,
            pixelsPerFrame = 10f,
            fps = 24
        )

        assertEquals(50L, snapped)
    }
}
