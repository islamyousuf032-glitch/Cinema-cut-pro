package com.example.timeline.media

import android.net.Uri
import kotlinx.coroutines.flow.Flow

sealed class TranscodeProgress {
    object Setup : TranscodeProgress()
    data class Progress(val percent: Float) : TranscodeProgress()
    object Completed : TranscodeProgress()
    data class Error(val exception: Exception) : TranscodeProgress()
}

interface TranscoderBackend {
    val isAvailable: Boolean
    val name: String

    fun transcode(
        sourceUri: Uri,
        outputUri: Uri,
        settings: ProxySettings
    ): Flow<TranscodeProgress>

    suspend fun cancel()
}
