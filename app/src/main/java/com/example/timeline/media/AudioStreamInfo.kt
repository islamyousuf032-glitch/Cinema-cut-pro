package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class AudioStreamInfo(
    val codecMime: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Int,
    val durationUs: Long,
    val language: String?,
    val hardwareDecodeSupported: Boolean
)
