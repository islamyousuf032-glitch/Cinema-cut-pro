package com.example.timeline.export.validation

import android.media.MediaCodec.CodecException
import java.io.IOException

object ExportErrorMapper {

    fun mapExceptionToError(exception: Throwable): ExportError {
        if (exception is ExportError) return exception

        val message = exception.message ?: "Unknown error occurred"

        if (exception is CodecException) {
            return ExportError(ExportErrorType.ENCODER_INIT_FAILED, "Codec error: $message", exception)
        }

        if (exception is IOException) {
            if (message.contains("ENOSPC", ignoreCase = true) || message.contains("No space left", ignoreCase = true)) {
                return ExportError(ExportErrorType.OUT_OF_STORAGE, "Not enough storage space", exception)
            }
            if (message.contains("EACCES", ignoreCase = true) || message.contains("Permission denied", ignoreCase = true)) {
                return ExportError(ExportErrorType.OUTPUT_NOT_WRITABLE, "Output path is not writable", exception)
            }
            return ExportError(ExportErrorType.UNKNOWN, "IO error: $message", exception)
        }

        if (message.contains("cancelled", ignoreCase = true)) {
            return ExportError(ExportErrorType.CANCELLED, "Export cancelled by user", exception)
        }

        return ExportError(ExportErrorType.UNKNOWN, message, exception)
    }

    fun getRecoveryActions(error: ExportError): List<ExportRecoveryAction> {
        return when (error.type) {
            ExportErrorType.MISSING_MEDIA -> listOf(ExportRecoveryAction.RELINK_MEDIA)
            ExportErrorType.OUTPUT_NOT_WRITABLE -> listOf(ExportRecoveryAction.CHOOSE_OUTPUT_FOLDER)
            ExportErrorType.CODEC_UNSUPPORTED -> listOf(ExportRecoveryAction.CHANGE_CODEC)
            ExportErrorType.CONTAINER_UNSUPPORTED -> listOf(ExportRecoveryAction.CHANGE_CONTAINER)
            ExportErrorType.ENCODER_INIT_FAILED -> listOf(ExportRecoveryAction.CHANGE_CODEC, ExportRecoveryAction.USE_LOWER_RESOLUTION)
            ExportErrorType.DECODER_FAILED -> listOf(ExportRecoveryAction.RETRY)
            ExportErrorType.NATIVE_BACKEND_MISSING -> listOf(ExportRecoveryAction.INSTALL_BACKEND)
            ExportErrorType.FFMPEG_MISSING -> listOf(ExportRecoveryAction.INSTALL_BACKEND)
            ExportErrorType.MUXER_FAILED -> listOf(ExportRecoveryAction.CHANGE_CONTAINER)
            ExportErrorType.OUT_OF_STORAGE -> listOf(ExportRecoveryAction.CHOOSE_OUTPUT_FOLDER)
            ExportErrorType.CANCELLED -> listOf(ExportRecoveryAction.RETRY)
            ExportErrorType.UNKNOWN -> listOf(ExportRecoveryAction.RETRY)
        }
    }
}
