package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class ColorMetadata(
    val colorStandard: Int? = null,
    val colorTransfer: Int? = null,
    val colorRange: Int? = null,
    val isHdr: Boolean = false,
    val isLog: Boolean = false,
    val detectedLogProfile: String? = null
)
