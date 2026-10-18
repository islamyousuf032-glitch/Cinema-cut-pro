package com.example.timeline.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import kotlinx.coroutines.CancellationException

class ProxyGenerationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        fun invalidInput(message: String) = Result.failure(
            Data.Builder().putString(KEY_ERROR_MSG, message).build()
        )
        val sourceUriString = inputData.getString(KEY_SOURCE_URI)
            ?: return invalidInput("Proxy source URI is missing.")
        val outputUriString = inputData.getString(KEY_OUTPUT_URI)
            ?: return invalidInput("Proxy output URI is missing.")
        
        val sourceWidth = inputData.getInt(KEY_SOURCE_WIDTH, 1920)
        val sourceHeight = inputData.getInt(KEY_SOURCE_HEIGHT, 1080)
        val hasAudio = inputData.getBoolean(KEY_HAS_AUDIO, true)
        
        // Settings could be parsed from inputData
        val settings = ProxySettings(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            videoMimeType = "video/avc",
            audioMimeType = if (hasAudio) androidx.media3.common.MimeTypes.AUDIO_AAC else null
        )
        
        val backend = Media3TranscoderBackend(applicationContext)
        var success = false
        var isCancelledLocally = false
        var failureMessage: String? = null
        
        try {
            backend.transcode(
                android.net.Uri.parse(sourceUriString), 
                android.net.Uri.parse(outputUriString), 
                settings
            ).collect { progress ->
                if (isStopped) {
                    backend.cancel()
                    isCancelledLocally = true
                    return@collect
                }
                
                when (progress) {
                    is TranscodeProgress.Setup -> {
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, 0).putString(KEY_STATUS, "ANALYZING").build())
                    }
                    is TranscodeProgress.Progress -> {
                        val pct = (progress.percent * 100).toInt()
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, pct).putString(KEY_STATUS, "RUNNING").build())
                    }
                    is TranscodeProgress.Completed -> {
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, 100).putString(KEY_STATUS, "COMPLETED").build())
                        success = true
                    }
                    is TranscodeProgress.Error -> {
                        success = false
                        failureMessage = progress.exception.message ?: "Media3 Transformer could not create the proxy."
                        setProgress(Data.Builder().putString(KEY_ERROR_MSG, failureMessage).build())
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            backend.cancel()
            throw cancelled
        } catch (e: Exception) {
            backend.cancel()
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MSG, "Proxy generation failed: ${e.message ?: e.javaClass.simpleName}").build()
            )
        }

        if (isCancelledLocally || isStopped) return Result.failure()

        return if (success) {
            Result.success(Data.Builder().putString(KEY_OUTPUT_URI, outputUriString).build())
        } else {
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MSG, failureMessage ?: "Media3 Transformer did not complete proxy generation.")
                    .build()
            )
        }
    }

    companion object {
        const val KEY_ASSET_ID = "asset_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_SOURCE_WIDTH = "source_width"
        const val KEY_SOURCE_HEIGHT = "source_height"
        const val KEY_HAS_AUDIO = "has_audio"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR_MSG = "error_msg"
    }
}
