package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
enum class DirectPreviewState {
    DIRECT_SUPPORTED_CONFIRMED,
    DIRECT_SUPPORTED_PROBABLE,
    TRY_ORIGINAL_ALLOWED,
    DIRECT_FAILED_RUNTIME,
    PROXY_RECOMMENDED,
    PROXY_REQUIRED,
    UNSUPPORTED,
    PLUGIN_REQUIRED
}
