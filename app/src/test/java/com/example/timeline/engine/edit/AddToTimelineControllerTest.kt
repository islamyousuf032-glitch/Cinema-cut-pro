package com.example.timeline.engine.edit

import com.example.timeline.core.ProjectFrameRatePreset
import com.example.timeline.core.ProjectSettings
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack
import com.example.timeline.core.TrackType
import com.example.timeline.media.FrameRate
import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MediaAssetType
import com.example.timeline.media.MediaMetadata
import com.example.timeline.media.VideoStreamInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AddToTimelineControllerTest {
    private fun project(tracks: List<TimelineTrack> = emptyList()) = TimelineProject(
        id = "project",
        settings = ProjectSettings(
            projectId = "project",
            projectName = "Test project",
            frameRate = ProjectFrameRatePreset.FPS_24
        ),
        tracks = tracks
    )

    private fun videoAsset() = MediaAsset(
        projectId = "project",
        displayName = "test.mp4",
        originalUriString = "file:///test.mp4",
        persistedUriPermission = true,
        mimeType = "video/mp4",
        fileSizeBytes = 0L,
        importedAt = 0L,
        modifiedAt = 0L,
        assetType = MediaAssetType.VIDEO,
        metadata = MediaMetadata(
            durationUs = 18_300_000L,
            durationFramesInSourceRate = 439L,
            containerFormat = "mp4",
            bitrate = 0L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            pixelAspectRatio = 1f,
            videoStreams = emptyList<VideoStreamInfo>(),
            audioStreams = emptyList(),
            hasVideo = true,
            hasAudio = false,
            isVariableFrameRate = false,
            estimatedFrameRate = FrameRate.FPS_24,
            exactFrameRate = FrameRate.FPS_24,
            timecodeStart = null,
            creationTime = null
        )
    )

    @Test
    fun firstImportedVideoStartsAtFrameZeroEvenWhenPlayheadIsNotAtZero() {
        val (_, clip) = AddToTimelineController.createAddClipCommand(
            project = project(),
            mediaAsset = videoAsset(),
            playheadFrame = 16L
        )

        assertEquals(0L, clip.timelineStart)
    }

    @Test
    fun firstImportedVideoStartsAtFrameZeroWhenProjectAlreadyHasAudio() {
        val audioTrack = TimelineTrack(
            id = "audio-track",
            name = "Audio 1",
            type = TrackType.AUDIO,
            clips = listOf(
                TimelineClip(
                    id = "audio-bed",
                    trackId = "audio-track",
                    mediaId = "audio-media",
                    name = "music.wav",
                    sourceIn = 0L,
                    sourceOut = 200L,
                    timelineStart = 0L
                )
            )
        )

        val (_, clip) = AddToTimelineController.createAddClipCommand(
            project = project(listOf(audioTrack)),
            mediaAsset = videoAsset(),
            playheadFrame = 72L
        )

        assertEquals(0L, clip.timelineStart)
    }

    @Test
    fun firstEnabledVideoUsesANewEmptyTrackWhenExistingVideoItemsAreDisabled() {
        val disabledVideoTrack = TimelineTrack(
            id = "disabled-track",
            name = "Video 1",
            type = TrackType.VIDEO,
            clips = listOf(
                TimelineClip(
                    id = "disabled-clip",
                    trackId = "disabled-track",
                    mediaId = "old-media",
                    name = "disabled.mp4",
                    sourceIn = 0L,
                    sourceOut = 80L,
                    timelineStart = 0L,
                    isEnabled = false
                )
            )
        )

        val (updated, clip) = AddToTimelineController.createAddClipCommand(
            project = project(listOf(disabledVideoTrack)),
            mediaAsset = videoAsset(),
            playheadFrame = 72L
        )

        assertEquals(0L, clip.timelineStart)
        org.junit.Assert.assertTrue(clip.trackId != disabledVideoTrack.id)
        assertEquals(2, updated.tracks.size)
        assertEquals("disabled-clip", updated.tracks.first().clips.single().id)
    }

    @Test
    fun laterImportedVideoStillUsesPlayheadWhenThereIsNoCollision() {
        val videoTrack = TimelineTrack(
            id = "video-track",
            name = "Video 1",
            type = TrackType.VIDEO,
            clips = listOf(
                TimelineClip(
                    id = "existing",
                    trackId = "video-track",
                    mediaId = "existing-media",
                    name = "existing.mp4",
                    sourceIn = 0L,
                    sourceOut = 100L,
                    timelineStart = 0L
                )
            )
        )

        val (_, clip) = AddToTimelineController.createAddClipCommand(
            project = project(listOf(videoTrack)),
            mediaAsset = videoAsset(),
            playheadFrame = 120L
        )

        assertEquals(120L, clip.timelineStart)
    }
}
