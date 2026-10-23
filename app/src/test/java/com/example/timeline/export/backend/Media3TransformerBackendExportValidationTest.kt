package com.example.timeline.export.backend

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.timeline.core.ClipType
import com.example.timeline.core.ProjectFrameRatePreset
import com.example.timeline.core.ProjectSettings
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack
import com.example.timeline.core.TrackType
import com.example.timeline.export.model.ExportSettings
import com.example.timeline.export.preset.SocialExportPresets
import com.example.timeline.media.AudioStreamInfo
import com.example.timeline.media.FrameRate
import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MediaAssetType
import com.example.timeline.media.MediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Media3TransformerBackendExportValidationTest {
    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    private class CodecProbe(
        private val anyVideoEncoder: Boolean = true,
        private val hardwareVideoEncoder: Boolean = true,
        private val audioEncoder: Boolean = true
    ) : Media3CodecSupportProbe {
        override fun hasVideoEncoder(settings: ExportSettings, requireHardware: Boolean): Boolean =
            if (requireHardware) hardwareVideoEncoder else anyVideoEncoder

        override fun hasAudioEncoder(sampleRate: Int, channelCount: Int, bitrate: Int): Boolean = audioEncoder
    }

    private fun asset(
        id: String,
        audioSampleRate: Int? = null,
        audioChannels: Int = 2
    ) = MediaAsset(
        assetId = id,
        projectId = "project",
        displayName = "$id.mp4",
        originalUriString = "file:///tmp/$id.mp4",
        persistedUriPermission = true,
        mimeType = "video/mp4",
        fileSizeBytes = 2_000_000L,
        importedAt = 1L,
        modifiedAt = 1L,
        assetType = MediaAssetType.VIDEO,
        metadata = MediaMetadata(
            durationUs = 10_000_000L,
            durationFramesInSourceRate = 300L,
            containerFormat = "mp4",
            bitrate = 8_000_000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            pixelAspectRatio = 1f,
            videoStreams = emptyList(),
            audioStreams = audioSampleRate?.let { sampleRate ->
                listOf(
                    AudioStreamInfo(
                        codecMime = "audio/aac",
                        sampleRate = sampleRate,
                        channelCount = audioChannels,
                        bitrate = 128_000,
                        durationUs = 10_000_000L,
                        language = null,
                        hardwareDecodeSupported = true
                    )
                )
            }.orEmpty(),
            hasVideo = true,
            hasAudio = audioSampleRate != null,
            isVariableFrameRate = false,
            estimatedFrameRate = FrameRate(30, 1),
            exactFrameRate = FrameRate(30, 1),
            timecodeStart = null,
            creationTime = null,
            variableFrameRateDetectionComplete = true
        )
    )

    private fun clip(id: String, mediaId: String, start: Long) = TimelineClip(
        id = id,
        trackId = "video",
        mediaId = mediaId,
        name = "$id.mp4",
        sourceIn = 0L,
        sourceOut = 100L,
        timelineStart = start,
        type = ClipType.MEDIA
    )

    private fun project(
        assets: List<MediaAsset>,
        videoClips: List<TimelineClip>,
        withSeparateAudioTrack: Boolean = false
    ): TimelineProject {
        val tracks = buildList {
            add(TimelineTrack(id = "video", name = "Video", type = TrackType.VIDEO, clips = videoClips))
            if (withSeparateAudioTrack) {
                add(
                    TimelineTrack(
                        id = "audio",
                        name = "Audio",
                        type = TrackType.AUDIO,
                        clips = listOf(clip("audio-clip", assets.first().assetId, 0L))
                    )
                )
            }
        }
        return TimelineProject(
            id = "project",
            settings = ProjectSettings(
                projectId = "project",
                projectName = "Export test",
                frameRate = ProjectFrameRatePreset.FPS_30
            ),
            tracks = tracks,
            mediaAssets = assets
        )
    }

    private fun settings(includeAudio: Boolean = false) = SocialExportPresets
        .youtube1080p("project", "validation_test")
        .copy(includeAudio = includeAudio, frameRate = com.example.timeline.export.model.ExportFrameRate.FPS_30)

    @Test
    fun standardCfrSingleTrackProjectPassesWithAnAvailableH264Encoder() {
        val source = asset("source")
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(), project)

        assertTrue(result.unsupportedFeatures.joinToString("\n"), result.isValid)
    }

    @Test
    fun videoOnlyExportDoesNotFailForMixedEmbeddedAudioOrSeparateAudioTracks() {
        val first = asset("first", audioSampleRate = 44_100)
        val second = asset("second", audioSampleRate = 48_000)
        val project = project(
            listOf(first, second),
            listOf(clip("first-clip", first.assetId, 0L), clip("second-clip", second.assetId, 100L)),
            withSeparateAudioTrack = true
        )
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(includeAudio = false), project)

        assertTrue(result.unsupportedFeatures.joinToString("\n"), result.isValid)
        assertFalse(shouldIncludeEmbeddedAudio(settings(includeAudio = false), trackMuted = false, clipHasAudio = listOf(true, true)))
    }

    @Test
    fun enabledAudioReportsSampleRateMismatchAndCanBeDisabled() {
        val first = asset("first", audioSampleRate = 44_100)
        val second = asset("second", audioSampleRate = 48_000)
        val project = project(
            listOf(first, second),
            listOf(clip("first-clip", first.assetId, 0L), clip("second-clip", second.assetId, 100L))
        )
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(includeAudio = true).copy(audioSampleRate = 44_100), project)

        assertFalse(result.isValid)
        assertTrue(result.unsupportedFeatures.any { it.contains("sample rate and channels must match") })
    }

    @Test
    fun compatibleSoftwareEncoderIsNotRejectedJustBecauseHardwareWasNotDetected() {
        val source = asset("source")
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(
            applicationContext,
            CodecProbe(anyVideoEncoder = true, hardwareVideoEncoder = false)
        )

        val result = backend.validate(settings(), project)

        assertTrue(result.unsupportedFeatures.joinToString("\n"), result.isValid)
        assertTrue(result.warnings.any { it.contains("software encoder") })
    }
}
