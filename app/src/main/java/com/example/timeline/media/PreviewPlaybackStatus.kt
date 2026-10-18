package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
enum class PreviewPlaybackStatus {
    NOT_TESTED,
    TRY_ORIGINAL_ALLOWED,
    DIRECT_SUPPORTED_PROBABLE,
    DIRECT_SUPPORTED_CONFIRMED,
    DIRECT_FAILED_RUNTIME,
    PROXY_RECOMMENDED,
    PROXY_REQUIRED,
    PROXY_GENERATING,
    PROXY_READY,
    STILL_FRAME_ONLY,
    UNSUPPORTED,
    READY,
    DIRECT_FAILED
}
