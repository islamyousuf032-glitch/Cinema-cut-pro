package com.example.timeline.engine.preview

import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MediaAssetType
import com.example.timeline.media.MediaMetadata
import com.example.timeline.media.ProxyInfo
import com.example.timeline.media.ProxyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSourceResolverTest {
    @Test
    fun originalSourceCarriesMimeAndPrefersLocalCopy() {
        val source = PreviewSourceResolver.resolveSource(
            asset(localUri = "/data/user/0/example/files/source.mp4"),
            preferProxy = false
        )

        assertNotNull(source)
        assertEquals("/data/user/0/example/files/source.mp4", source?.uri)
        assertEquals("video/mp4", source?.mimeType)
        assertFalse(source?.isProxy ?: true)
    }

    @Test
    fun preferredReadyProxyCarriesItsOwnMimeType() {
        val source = PreviewSourceResolver.resolveSource(
            asset(proxyReady = true),
            preferProxy = true
        )

        assertNotNull(source)
        assertEquals("file:///proxy/proxy.mp4", source?.uri)
        assertEquals("video/mp4", source?.mimeType)
        assertTrue(source?.isProxy ?: false)
    }

    @Test
    fun proxyPlaybackFailureKeepsGeneratedStatusButRoutesToOriginal() {
        val source = PreviewSourceResolver.resolveSource(
            asset(
                localUri = "/data/user/0/example/files/source.mp4",
                proxyReady = true
            ).copy(proxyPlaybackFailure = "Proxy renderer failed"),
            preferProxy = true
        )

        assertNotNull(source)
        assertEquals("/data/user/0/example/files/source.mp4", source?.uri)
        assertEquals("video/mp4", source?.mimeType)
        assertFalse(source?.isProxy ?: true)
    }

    @Test
    fun forceOriginalOverridesProxyPreferenceAndDirectFailureFallback() {
        val source = PreviewSourceResolver.resolveSource(
            asset(
                localUri = "/data/user/0/example/files/source.mp4",
                proxyReady = true
            ).copy(previewStatus = com.example.timeline.media.PreviewStatus.DIRECT_FAILED_RUNTIME),
            preferProxy = true,
            forceOriginal = true
        )

        assertNotNull(source)
        assertEquals("/data/user/0/example/files/source.mp4", source?.uri)
        assertEquals("video/mp4", source?.mimeType)
        assertFalse(source?.isProxy ?: true)
    }

    private fun asset(
        localUri: String? = null,
        proxyReady: Boolean = false
    ) = MediaAsset(
        projectId = "project",
        displayName = "source.mp4",
        originalUriString = "content://media/source.mp4",
        localOriginalUriString = localUri,
        persistedUriPermission = true,
        mimeType = "video/mp4",
        fileSizeBytes = 128L,
        importedAt = 0L,
        modifiedAt = 0L,
        assetType = MediaAssetType.VIDEO,
        proxyStatus = if (proxyReady) ProxyStatus.READY else ProxyStatus.NONE,
        metadata = MediaMetadata(
            durationUs = 1_000_000L,
            durationFramesInSourceRate = 30L,
            containerFormat = "mp4",
            bitrate = 1_000_000L,
            width = 320,
            height = 240,
            rotationDegrees = 0,
            pixelAspectRatio = 1f,
            videoStreams = emptyList(),
            audioStreams = emptyList(),
            hasVideo = true,
            hasAudio = false,
            isVariableFrameRate = false,
            estimatedFrameRate = null,
            exactFrameRate = null,
            timecodeStart = null,
            creationTime = 0L
        ),
        proxyInfo = if (proxyReady) {
            ProxyInfo(
                uriString = "file:///proxy/proxy.mp4",
                width = 160,
                height = 120,
                frameRate = 30f,
                isProxyGenerated = true,
                proxyFormat = "h264",
                proxyMimeType = "video/avc"
            )
        } else {
            null
        }
    )
}
