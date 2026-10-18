package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
enum class PreviewStatus {
    NOT_TESTED,
    TRY_ORIGINAL_ALLOWED,
    BROWSER_PLAYABLE,
    DIRECT_SUPPORTED_PROBABLE,
    DIRECT_SUPPORTED_CONFIRMED,
    DIRECT_FAILED_RUNTIME,
    PROXY_RECOMMENDED,
    PROXY_REQUIRED,
    PROXY_GENERATING,
    PROXY_READY,
    STILL_FRAME_ONLY,
    UNSUPPORTED
}

@Serializable
enum class ProxyStatus {
    NONE,
    RECOMMENDED,
    REQUIRED,
    GENERATING,
    READY,
    FAILED
}

@Serializable
enum class MediaErrorStatus {
    NONE,
    FILE_UNREADABLE,
    ANALYSIS_FAILED,
    TRANSCODE_FAILED,
    DECODE_FAILED,
    UNKNOWN_ERROR
}
