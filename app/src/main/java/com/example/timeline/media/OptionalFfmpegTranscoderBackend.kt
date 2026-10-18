package com.example.timeline.media

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OptionalFfmpegTranscoderBackend : TranscoderBackend {
    override val isAvailable: Boolean = false // FFmpeg not added by default
    override val name: String = "FFmpeg (Optional)"

    override fun transcode(
        sourceUri: Uri,
        outputUri: Uri,
        settings: ProxySettings
    ): Flow<TranscodeProgress> = flow {
        emit(TranscodeProgress.Error(UnsupportedOperationException("TRANSCODE_BACKEND_NOT_AVAILABLE")))
    }

    override suspend fun cancel() {
        // No-op
    }
}
