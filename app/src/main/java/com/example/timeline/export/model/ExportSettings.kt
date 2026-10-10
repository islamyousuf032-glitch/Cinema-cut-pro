package com.example.timeline.export.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportSettings(
    val exportId: String,
    val projectId: String,
    val outputFileName: String,
    val outputDirectoryUri: String,
    val resolutionWidth: Int,
    val resolutionHeight: Int,
    val frameRate: ExportFrameRate,
    val codec: ExportCodec,
    val container: ExportContainer,
    val bitrateMode: BitrateMode,
    val videoBitrate: Int,
    val maxBitrate: Int,
    val qualityPreset: ExportQualityPreset,
    val keyframeInterval: Int,
    val profile: String? = null,
    val level: String? = null,
    val useHardwareEncoder: Boolean = true,
    val useOriginalMedia: Boolean = true,
    val allowProxyExport: Boolean = false,
    val renderAlpha: Boolean = false,
    val exportHDR: Boolean = false,
    val colorOutput: ExportColorSettings.ColorOutput = ExportColorSettings.ColorOutput.Rec709,
    val toneMappingMode: ExportColorSettings.ToneMappingMode = ExportColorSettings.ToneMappingMode.STANDARD,
    val audioCodec: ExportAudioSettings.AudioCodec = ExportAudioSettings.AudioCodec.AAC,
    val audioBitrate: Int = 128000,
    val audioSampleRate: Int = 48000,
    val audioChannels: Int = 2,
    val normalizeAudio: Boolean = false,
    val includeWatermark: Boolean = false,
    val watermarkSettings: WatermarkSettings? = null,
    val socialPreset: String? = null,
    val batchExportGroupId: String? = null
) {
    enum class BitrateMode {
        AUTO,
        CBR,
        VBR,
        CRF
    }
}
