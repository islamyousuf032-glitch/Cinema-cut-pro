package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class CodecCapability(
    val mimeType: String,
    val isHardwareAccelerated: Boolean,
    val isSoftwareOnly: Boolean,
    val maxInstances: Int? = null,
    val maxSupportedWidth: Int? = null,
    val maxSupportedHeight: Int? = null
)
