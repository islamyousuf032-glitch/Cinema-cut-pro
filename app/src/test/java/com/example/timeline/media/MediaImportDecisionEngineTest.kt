package com.example.timeline.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaImportDecisionEngineTest {
    private val decisionEngine = MediaImportDecisionEngine()

    @Test
    fun hardwareDecoded1080p30DoesNotRecommendProxy() {
        val result = decisionEngine.decideImportStatus(
            analysis(width = 1920, height = 1080, frameRate = 30f, hardware = true)
        )

        assertTrue(result.report.directPlaybackSupport)
        assertFalse(result.report.proxyRecommendation)
        assertEquals(ProxyStatus.NONE, result.proxyStatus)
    }

    @Test
    fun softwareOnlyDecoderRecommendsProxyWithoutCallingFormatUnsupported() {
        val result = decisionEngine.decideImportStatus(
            analysis(width = 1280, height = 720, frameRate = 30f, software = true)
        )

        assertTrue(result.report.directPlaybackSupport)
        assertTrue(result.report.proxyRecommendation)
        assertFalse(result.report.transcodeRecommendation)
        assertEquals(ProxyStatus.RECOMMENDED, result.proxyStatus)
        assertTrue(result.report.warnings.any { it.contains("hardware decoder was not confirmed") })
    }

    @Test
    fun unknownDecoderClassificationRecommendsProxyRatherThanAssumingHardware() {
        val result = decisionEngine.decideImportStatus(
            analysis(width = 1280, height = 720, frameRate = 30f, unknown = true)
        )

        assertTrue(result.report.directPlaybackSupport)
        assertTrue(result.report.proxyRecommendation)
        assertFalse(result.report.transcodeRecommendation)
        assertEquals(ProxyStatus.RECOMMENDED, result.proxyStatus)
    }

    @Test
    fun decoderCapabilityChangesAreVisibleInVideoStreamEquality() {
        val noDecoder = analysis(width = 1280, height = 720, frameRate = 30f).videoStreams.single()
        val softwareDecoder = noDecoder.copy(
            softwareDecodeSupported = true,
            requiresProxy = true,
            requiresTranscode = false
        )

        assertFalse(noDecoder == softwareDecoder)
    }

    @Test
    fun noCompatibleDecoderRequiresProxyOrSupportedTranscode() {
        val result = decisionEngine.decideImportStatus(
            analysis(width = 1920, height = 1080, frameRate = 30f)
        )

        assertFalse(result.report.directPlaybackSupport)
        assertFalse(result.report.proxyRecommendation)
        assertTrue(result.report.transcodeRecommendation)
        assertEquals(PreviewStatus.UNSUPPORTED, result.previewStatus)
        assertEquals(ProxyStatus.REQUIRED, result.proxyStatus)
    }

    @Test
    fun highDecodeWorkloadRecommendsProxyEvenWithHardwareDecoder() {
        val result = decisionEngine.decideImportStatus(
            analysis(width = 3840, height = 2160, frameRate = 30f, hardware = true)
        )

        assertTrue(result.report.directPlaybackSupport)
        assertTrue(result.report.proxyRecommendation)
        assertEquals(ProxyStatus.RECOMMENDED, result.proxyStatus)
    }

    private fun analysis(
        width: Int,
        height: Int,
        frameRate: Float,
        hardware: Boolean = false,
        software: Boolean = false,
        unknown: Boolean = false
    ) = MediaAnalysisResult(
        durationUs = 10_000_000,
        width = width,
        height = height,
        rotation = 0,
        frameRate = frameRate,
        defaultMimeType = "video/avc",
        videoStreams = listOf(
            VideoStreamInfo(
                codecName = "avc",
                codecMime = "video/avc",
                codecProfile = null,
                codecLevel = null,
                width = width,
                height = height,
                frameRate = FrameRate((frameRate * 1000).toInt(), 1000),
                bitDepth = null,
                chromaSubsampling = null,
                colorStandard = null,
                colorTransfer = null,
                colorRange = null,
                isHdr = false,
                isLog = false,
                detectedLogProfile = null,
                hardwareDecodeSupported = hardware,
                softwareDecodeSupported = software,
                requiresProxy = !hardware && (software || unknown),
                requiresTranscode = !hardware && !software && !unknown,
                decoderSupportUnknown = unknown
            )
        ),
        audioStreams = emptyList(),
        hasVideo = true,
        hasAudio = false,
        isVariableFrameRate = false,
        variableFrameRateDetectionComplete = true
    )
}
