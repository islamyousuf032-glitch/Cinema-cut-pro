package com.example.timeline.media

data class ProxySettings(
    val sourceWidth: Int = 1920,
    val sourceHeight: Int = 1080,
    val videoBitrate: Int = 5_000_000,
    val audioBitrate: Int = 128_000,
    val videoMimeType: String = "video/avc", // H.264
    val audioMimeType: String? = null, // Don't force transcode
    val forceSdr: Boolean = true // Convert HDR to SDR for proxy
)
