package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
enum class MediaAssetType {
    VIDEO,
    AUDIO,
    IMAGE,
    IMAGE_SEQUENCE,
    NESTED_SEQUENCE,
    UNKNOWN
}
