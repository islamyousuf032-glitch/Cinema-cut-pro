package com.example.timeline.export.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportAudioSettings(
    val codec: AudioCodec = AudioCodec.AAC,
    val bitrate: Int = 128_000,
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val normalizeAudio: Boolean = false
) {
    enum class AudioCodec {
        AAC,
        PCM_WAV,
        OPUS
    }
}
