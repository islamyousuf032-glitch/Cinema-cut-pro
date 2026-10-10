package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class MediaMetadata(
    val durationUs: Long,
    val durationFramesInSourceRate: Long,
    val containerFormat: String,
    val bitrate: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val pixelAspectRatio: Float,
    val videoStreams: List<VideoStreamInfo>,
    val audioStreams: List<AudioStreamInfo>,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val isVariableFrameRate: Boolean,
    val estimatedFrameRate: FrameRate?,
    val exactFrameRate: FrameRate?,
    val timecodeStart: String?,
    val creationTime: Long?,
    val originalFilename: String? = null
)
