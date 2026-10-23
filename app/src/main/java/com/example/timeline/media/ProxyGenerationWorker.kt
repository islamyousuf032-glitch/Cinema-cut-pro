package com.example.timeline.media

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.media3.transformer.ExportException
import kotlinx.coroutines.CancellationException
import java.io.File

class ProxyGenerationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private fun describeFailure(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.toList()
        val exportError = causes.filterIsInstance<ExportException>().firstOrNull()
        val reason = error.message?.takeIf { it.isNotBlank() }
            ?: causes.lastOrNull()?.message?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        val details = buildString {
            append("Proxy generation failed")
            exportError?.let { append(" — ${it.errorCodeName} (${it.errorCode})") }
            append(": ")
            append(reason)
            val rootCause = causes.lastOrNull()
            val rootMessage = rootCause?.message?.takeIf { it.isNotBlank() }
            if (rootCause != null && rootCause !== error && rootMessage != null && rootMessage != reason) {
                append(" — caused by ${rootCause.javaClass.simpleName}: ")
                append(rootMessage)
            }
        }
        return details
            .replace(Regex("""(?i)(?:file|content)://[^\s]+"""), "[media URI]")
            .replace(Regex("""/(?:data|storage|sdcard)/[^\s,;)]*""", RegexOption.IGNORE_CASE), "[media path]")
            .take(700)
    }

    override suspend fun doWork(): Result {
        fun invalidInput(message: String) = Result.failure(
            Data.Builder().putString(KEY_ERROR_MSG, message).build()
        )
        val sourceUriString = inputData.getString(KEY_SOURCE_URI)
            ?: return invalidInput("Proxy source URI is missing.")
        val outputUriString = inputData.getString(KEY_OUTPUT_URI)
            ?: return invalidInput("Proxy output URI is missing.")
        if (sourceUriString.isBlank()) return invalidInput("Proxy source URI is empty.")
        if (outputUriString.isBlank()) return invalidInput("Proxy output URI is empty.")
        val sourceUri = Uri.parse(sourceUriString)
        if (sourceUri.scheme.isNullOrBlank()) {
            return invalidInput("Proxy source must be a readable file or content URI.")
        }
        val outputUri = Uri.parse(outputUriString)
        if (outputUri.scheme != "file" || outputUri.path.isNullOrBlank()) {
            return invalidInput("Proxy output must be a local file URI.")
        }
        
        val sourceWidth = inputData.getInt(KEY_SOURCE_WIDTH, 1920)
        val sourceHeight = inputData.getInt(KEY_SOURCE_HEIGHT, 1080)
        val hasAudio = inputData.getBoolean(KEY_HAS_AUDIO, true)
        val sourceIsHdr = inputData.getBoolean(KEY_SOURCE_HDR, false)
        
        val settings = ProxySettings(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            videoMimeType = "video/avc",
            audioMimeType = if (hasAudio) androidx.media3.common.MimeTypes.AUDIO_AAC else null,
            sourceIsHdr = sourceIsHdr,
            forceSdr = true
        )
        
        try {
            setForeground(ProxyForegroundNotification.create(applicationContext, id, progressPercent = null))
        } catch (failure: Exception) {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MSG, describeFailure(failure)).build()
            )
        }

        val backend = Media3TranscoderBackend(applicationContext)
        var success = false
        var isCancelledLocally = false
        var failureMessage: String? = null
        var lastForegroundPercent = -1
        
        try {
            backend.transcode(
                sourceUri,
                outputUri,
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
                        val pct = (progress.percent * 100).toInt().coerceIn(0, 99)
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, pct).putString(KEY_STATUS, "RUNNING").build())
                        if (pct != lastForegroundPercent) {
                            setForeground(ProxyForegroundNotification.create(applicationContext, id, pct))
                            lastForegroundPercent = pct
                        }
                    }
                    is TranscodeProgress.Completed -> {
                        // Completion from Transformer is only provisional until the MP4 can be
                        // reopened and its encoded video (and requested audio) samples are checked.
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, 99).putString(KEY_STATUS, "VALIDATING").build())
                        success = true
                    }
                    is TranscodeProgress.Error -> {
                        success = false
                        failureMessage = describeFailure(progress.exception)
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
                Data.Builder().putString(KEY_ERROR_MSG, describeFailure(e)).build()
            )
        }

        if (isCancelledLocally || isStopped) return Result.failure()

        if (success) {
            val outputFile = outputUri.path?.let(::File)
                ?: return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MSG, "Proxy output file path is unavailable.")
                        .build()
                )
            try {
                ProxyOutputValidator.verify(outputFile, expectAudio = hasAudio)
            } catch (failure: Exception) {
                outputFile.delete()
                return Result.failure(
                    Data.Builder().putString(KEY_ERROR_MSG, describeFailure(failure)).build()
                )
            }
            setProgress(Data.Builder().putInt(KEY_PROGRESS, 100).putString(KEY_STATUS, "COMPLETED").build())
            return Result.success(Data.Builder().putString(KEY_OUTPUT_URI, outputUriString).build())
        }

        return Result.failure(
            Data.Builder()
                .putString(KEY_ERROR_MSG, failureMessage ?: "Media3 Transformer did not complete proxy generation.")
                .build()
        )
    }

    companion object {
        const val KEY_ASSET_ID = "asset_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_SOURCE_WIDTH = "source_width"
        const val KEY_SOURCE_HEIGHT = "source_height"
        const val KEY_HAS_AUDIO = "has_audio"
        const val KEY_SOURCE_HDR = "source_hdr"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR_MSG = "error_msg"
    }
}
