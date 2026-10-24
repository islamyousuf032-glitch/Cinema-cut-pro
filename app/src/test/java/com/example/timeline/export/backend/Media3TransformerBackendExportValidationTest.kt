package com.example.timeline.export.backend

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
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
import com.example.timeline.media.VideoStreamInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(UnstableApi::class)
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
        audioChannels: Int = 2,
        videoStreams: List<VideoStreamInfo> = emptyList()
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
            videoStreams = videoStreams,
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

    private fun videoStream(bitDepth: Int = 8, isHdr: Boolean = false, isLog: Boolean = false) = VideoStreamInfo(
        codecName = "hevc",
        codecMime = "video/hevc",
        codecProfile = null,
        codecLevel = null,
        width = 3840,
        height = 2160,
        frameRate = FrameRate(30, 1),
        bitDepth = bitDepth,
        chromaSubsampling = "4:2:0",
        colorStandard = null,
        colorTransfer = when {
            isHdr -> android.media.MediaFormat.COLOR_TRANSFER_HLG
            bitDepth > 8 -> android.media.MediaFormat.COLOR_TRANSFER_SDR_VIDEO
            else -> null
        },
        colorRange = null,
        hdrStaticMetadata = null,
        hdrDynamicMetadata = null,
        isHdr = isHdr,
        isLog = isLog,
        detectedLogProfile = null,
        hardwareDecodeSupported = true,
        softwareDecodeSupported = false,
        requiresProxy = false,
        requiresTranscode = false
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
    fun hdrSourceIsValidatedForExplicitSdrToneMapping() {
        val source = asset("hdr", videoStreams = listOf(videoStream(bitDepth = 10, isHdr = true)))
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(), project)

        assertTrue(result.unsupportedFeatures.joinToString("\n"), result.isValid)
        assertTrue(result.warnings.any { it.contains("tone-mapped to Rec.709 SDR") })
        assertEquals(
            Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            hdrModeForExport(hasHdrInput = true)
        )
        assertEquals(Composition.HDR_MODE_KEEP_HDR, hdrModeForExport(hasHdrInput = false))
    }

    @Test
    fun tenBitSdrSourceCanBeConvertedToEightBitH264() {
        val source = asset("ten-bit-sdr", videoStreams = listOf(videoStream(bitDepth = 10)))
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(), project)

        assertTrue(result.unsupportedFeatures.joinToString("\n"), result.isValid)
        assertTrue(result.warnings.any { it.contains("converted to 8-bit H.264") })
    }

    @Test
    fun tenBitSourceWithUnknownTransferIsBlockedRatherThanAssumedSdr() {
        val stream = videoStream(bitDepth = 10).copy(colorTransfer = null)
        val source = asset("unknown-transfer", videoStreams = listOf(stream))
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(), project)

        assertFalse(result.isValid)
        assertTrue(result.unsupportedFeatures.any { it.contains("no verified SDR transfer metadata") })
    }

    @Test
    fun logSourceRemainsBlockedInsteadOfBeingSilentlyTreatedAsRec709() {
        val source = asset("log", videoStreams = listOf(videoStream(bitDepth = 10, isLog = true)))
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(), project)

        assertFalse(result.isValid)
        assertTrue(result.unsupportedFeatures.any { it.contains("Log-encoded") })
    }

    @Test
    @Config(sdk = [28])
    fun hdrSourceIsRejectedOnAndroidVersionsWithoutOpenGlToneMappingSupport() {
        val source = asset("hdr", videoStreams = listOf(videoStream(bitDepth = 10, isHdr = true)))
        val project = project(listOf(source), listOf(clip("clip", source.assetId, 0L)))
        val backend = Media3TransformerBackend(applicationContext, CodecProbe())

        val result = backend.validate(settings(), project)

        assertFalse(result.isValid)
        assertTrue(result.unsupportedFeatures.any { it.contains("HDR-to-SDR export requires Android 10") })
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
