package com.example.timeline.export.validation

enum class ExportErrorType {
    MISSING_MEDIA,
    OUTPUT_NOT_WRITABLE,
    CODEC_UNSUPPORTED,
    CONTAINER_UNSUPPORTED,
    ENCODER_INIT_FAILED,
    DECODER_FAILED,
    NATIVE_BACKEND_MISSING,
    FFMPEG_MISSING,
    MUXER_FAILED,
    OUT_OF_STORAGE,
    CANCELLED,
    UNKNOWN
}

data class ExportError(
    val type: ExportErrorType,
    override val message: String,
    val exception: Throwable? = null
) : Exception(message, exception)
