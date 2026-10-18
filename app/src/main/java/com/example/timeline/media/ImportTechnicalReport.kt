package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class ImportTechnicalReport(
    val detectedFormat: String,
    val directPlaybackSupport: Boolean,
    val proxyRecommendation: Boolean,
    val transcodeRecommendation: Boolean,
    val warnings: List<String>,
    val userFriendlyMessage: String
)
