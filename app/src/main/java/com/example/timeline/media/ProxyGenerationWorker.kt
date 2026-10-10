package com.example.timeline.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import kotlinx.coroutines.flow.first

class ProxyGenerationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val assetIdString = inputData.getString(KEY_ASSET_ID) ?: return Result.failure()
        val sourceUriString = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure()
        val outputUriString = inputData.getString(KEY_OUTPUT_URI) ?: return Result.failure()
        
        val sourceWidth = inputData.getInt(KEY_SOURCE_WIDTH, 1920)
        val sourceHeight = inputData.getInt(KEY_SOURCE_HEIGHT, 1080)
        val hasAudio = inputData.getBoolean(KEY_HAS_AUDIO, true)
        
        // Settings could be parsed from inputData
        val settings = ProxySettings(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            videoMimeType = "video/avc",
            audioMimeType = null
        )
        
        val backend = Media3TranscoderBackend(applicationContext)
        var success = false
        var isCancelledLocally = false
        
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
                        setProgress(Data.Builder().putString(KEY_ERROR_MSG, progress.exception.message).build())
                    }
                }
            }
        } catch (e: Exception) {
            return Result.failure(Data.Builder().putString(KEY_ERROR_MSG, "Setup Exception: ${e.message}").build())
        }
        
        if (isCancelledLocally || isStopped) {
            return Result.failure()
        }
        
        return if (success) {
            Result.success(Data.Builder().putString(KEY_OUTPUT_URI, outputUriString).build())
        } else {
            Result.failure() // Could also pass KEY_ERROR_MSG here if we tracked it
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
