package com.example.timeline.export.validation

import com.example.timeline.core.ProjectFrameRatePreset
import com.example.timeline.core.ProjectSettings
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack
import com.example.timeline.core.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineExportAutoFixerTest {
    private fun project(vararg tracks: TimelineTrack) = TimelineProject(
        id = "project",
        settings = ProjectSettings(
            projectId = "project",
            projectName = "Test project",
            frameRate = ProjectFrameRatePreset.FPS_24
        ),
        tracks = tracks.toList()
    )

    @Test
    fun compactsGappedClipsFromFrameZeroAndPreservesSourceTrims() {
        val first = TimelineClip(
            id = "first",
            trackId = "video",
            mediaId = "media-1",
            name = "first.mp4",
            sourceIn = 12L,
            sourceOut = 52L,
            timelineStart = 80L
        )
        val second = TimelineClip(
            id = "second",
            trackId = "video",
            mediaId = "media-2",
            name = "second.mp4",
            sourceIn = 30L,
            sourceOut = 90L,
            timelineStart = 0L
        )
        val original = project(
            TimelineTrack("video", "Video 1", TrackType.VIDEO, clips = listOf(first, second))
        )

        val repaired = TimelineExportAutoFixer.compactVisibleVideoTrack(original)!!
        val clips = repaired.tracks.single().clips.associateBy { it.id }

        assertEquals(0L, clips.getValue("second").timelineStart)
        assertEquals(60L, clips.getValue("first").timelineStart)
        assertEquals(first.sourceIn, clips.getValue("first").sourceIn)
        assertEquals(first.sourceOut, clips.getValue("first").sourceOut)
        assertEquals(second.sourceIn, clips.getValue("second").sourceIn)
        assertEquals(second.sourceOut, clips.getValue("second").sourceOut)
    }

    @Test
    fun refusesToRewriteLockedClipsOrMultipleVisibleVideoTracks() {
        val lockedClip = TimelineClip(
            id = "locked",
            trackId = "video-1",
            mediaId = "media",
            name = "locked.mp4",
            sourceIn = 0L,
            sourceOut = 30L,
            timelineStart = 40L,
            isLocked = true
        )
        val video1 = TimelineTrack(
            "video-1",
            "Video 1",
            TrackType.VIDEO,
            clips = listOf(lockedClip)
        )
        assertNull(TimelineExportAutoFixer.compactVisibleVideoTrack(project(video1)))

        val video2 = TimelineTrack(
            "video-2",
            "Video 2",
            TrackType.VIDEO,
            clips = listOf(
                TimelineClip(
                    id = "other",
                    trackId = "video-2",
                    mediaId = "media-2",
                    name = "other.mp4",
                    sourceIn = 0L,
                    sourceOut = 30L,
                    timelineStart = 0L
                )
            )
        )
        val visibleVideo1 = video1.copy(clips = listOf(lockedClip.copy(isLocked = false)))
        assertNull(TimelineExportAutoFixer.compactVisibleVideoTrack(project(visibleVideo1, video2)))
    }
}
