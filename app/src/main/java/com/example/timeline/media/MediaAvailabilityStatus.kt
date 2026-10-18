package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
enum class MediaAvailabilityStatus {
    AVAILABLE_LOCAL_COPY,
    AVAILABLE_BROWSER_BLOB,
    AVAILABLE_ORIGINAL_URI,
    MISSING_NEEDS_RELINK
}
