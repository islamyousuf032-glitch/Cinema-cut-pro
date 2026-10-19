package com.example.timeline.engine.preview

import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlaybackFailureTest {
    @Test
    fun frameProcessorFailuresAreNotClassifiedAsInvalidMedia() {
        val failure = PreviewPlaybackFailure(
            engineType = PreviewEngineType.MEDIA3_FALLBACK,
            uriScheme = "file",
            uriAuthority = null,
            mimeType = "video/mp4",
            errorCode = 4003,
            message = "Playback failed",
            causeType = "VideoFrameProcessingException",
            causeMessage = "Error adjusting video frame"
        )

        assertTrue(failure.isVideoEffectFailure)
    }

    @Test
    fun decoderFailuresAreNotClassifiedAsVideoEffectFailures() {
        val failure = PreviewPlaybackFailure(
            engineType = PreviewEngineType.MEDIA3_FALLBACK,
            uriScheme = "file",
            uriAuthority = null,
            mimeType = "video/mp4",
            errorCode = 4003,
            message = "Decoder failed",
            causeType = "MediaCodecRenderer",
            causeMessage = "No suitable decoder was found"
        )

        assertTrue(!failure.isVideoEffectFailure)
    }

    @Test
    fun userMessageIncludesMimeUriContextErrorCodeAndUnderlyingCause() {
        val failure = PreviewPlaybackFailure(
            engineType = PreviewEngineType.MEDIA3_FALLBACK,
            uriScheme = "content",
            uriAuthority = "com.android.providers.media.documents",
            mimeType = "video/mp4",
            errorCode = 3003,
            message = "Container is not supported by this extractor",
            causeType = "MediaCodecRenderer",
            causeMessage = "No suitable decoder was found",
            category = PreviewPlaybackFailureCategory.UNSUPPORTED_MEDIA,
            errorCodeName = "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"
        )

        val message = failure.toUserMessage()
        assertTrue(message.contains("does not support"))
        assertTrue(message.contains("video/mp4"))
        assertTrue(message.contains("content://com.android.providers.media.documents"))
        assertTrue(message.contains("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"))
        assertTrue(message.contains("No suitable decoder was found"))
    }
}
