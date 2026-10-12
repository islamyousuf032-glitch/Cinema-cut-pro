package com.example.timeline.ui

import androidx.compose.ui.geometry.Offset
import com.example.timeline.core.ProjectFrameRatePreset
import com.example.timeline.core.ProjectSettings
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack
import com.example.timeline.core.TrackType
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGestureControllerTest {
    @Test
    fun dragStartingOutsideTrackDoesNotMoveTheClipVertically() {
        val clip = TimelineClip(
            id = "clip",
            trackId = "video-track",
            mediaId = "media",
            name = "clip.mp4",
            sourceIn = 0L,
            sourceOut = 100L,
            timelineStart = 0L
        )
        val project = TimelineProject(
            id = "project",
            settings = ProjectSettings(
                projectId = "project",
                projectName = "Test project",
                frameRate = ProjectFrameRatePreset.FPS_24
            ),
            tracks = listOf(
                TimelineTrack("video-track", "Video 1", TrackType.VIDEO, clips = listOf(clip))
            )
        )

        val action = TimelineGestureController.determineDragAction(
            startOffset = Offset(x = 50f, y = 40f + 84f),
            scrollX = 0f,
            scrollY = 0f,
            rulerHeightPx = 40f,
            project = project,
            pixelsPerFrame = 1f,
            selectedClipId = clip.id
        )

        assertTrue(action is TimelineDragAction.Pan)
    }
}
