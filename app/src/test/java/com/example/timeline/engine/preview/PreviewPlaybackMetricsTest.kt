package com.example.timeline.engine.preview

import com.example.timeline.media.ProxyStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlaybackMetricsTest {
    private fun metrics(dropped: Long = 5L, windowMs: Long = 1_000L) = PreviewPlaybackMetrics(
        droppedFramesInLastReport = dropped,
        droppedFrameReportWindowMs = windowMs
    )

    private fun shouldQueueProxy(
        previewMode: PreviewMode = PreviewMode.SMOOTH_PLAYBACK,
        engineType: PreviewEngineType = PreviewEngineType.MEDIA3_FALLBACK,
        isPlaying: Boolean = true,
        isProxySource: Boolean = false,
        proxyStatus: ProxyStatus = ProxyStatus.NONE,
        proxyPlaybackFailure: String? = null,
        playbackMetrics: PreviewPlaybackMetrics = metrics()
    ) = shouldGenerateAdaptiveProxy(
        previewMode = previewMode,
        engineType = engineType,
        isPlaying = isPlaying,
        isProxySource = isProxySource,
        proxyStatus = proxyStatus,
        proxyPlaybackFailure = proxyPlaybackFailure,
        metrics = playbackMetrics
    )

    @Test
    fun repeatedDropsInSmoothPlaybackRequestOneAdaptiveProxy() {
        assertTrue(shouldQueueProxy())
    }

    @Test
    fun adaptiveProxyIsNotRequestedForAccuratePreviewOrAProxySource() {
        assertFalse(shouldQueueProxy(previewMode = PreviewMode.COLOR_ACCURATE))
        assertFalse(shouldQueueProxy(isProxySource = true))
    }

    @Test
    fun adaptiveProxyRequiresSustainedDropsAndAnEligibleMedia3Source() {
        assertFalse(shouldQueueProxy(playbackMetrics = metrics(dropped = 4L)))
        assertFalse(shouldQueueProxy(playbackMetrics = metrics(windowMs = 499L)))
        assertFalse(shouldQueueProxy(engineType = PreviewEngineType.FFMPEG_NATIVE))
        assertFalse(shouldQueueProxy(isPlaying = false))
        assertFalse(shouldQueueProxy(proxyStatus = ProxyStatus.GENERATING))
        assertFalse(shouldQueueProxy(proxyStatus = ProxyStatus.READY))
        assertFalse(
            shouldQueueProxy(
                proxyStatus = ProxyStatus.FAILED,
                proxyPlaybackFailure = "Proxy decoder failed"
            )
        )
    }
}
