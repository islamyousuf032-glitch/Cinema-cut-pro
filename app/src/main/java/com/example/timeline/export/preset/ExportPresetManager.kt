package com.example.timeline.export.preset

import com.example.timeline.export.model.ExportSettings

enum class ExportPresetType(val displayName: String) {
    YOUTUBE_1080P("YouTube 1080p"),
    YOUTUBE_4K("YouTube 4K"),
    YOUTUBE_SHORTS("YouTube Shorts"),
    TIKTOK("TikTok 1080x1920"),
    INSTAGRAM_REELS("Instagram Reels"),
    FACEBOOK("Facebook"),
    CUSTOM("Custom")
}

object ExportPresetManager {
    fun getSettingsForPreset(presetType: ExportPresetType, projectId: String, outputFileName: String): ExportSettings {
        return when (presetType) {
            ExportPresetType.YOUTUBE_1080P -> SocialExportPresets.youtube1080p(projectId, outputFileName)
            ExportPresetType.YOUTUBE_4K -> SocialExportPresets.youtube4K(projectId, outputFileName)
            ExportPresetType.YOUTUBE_SHORTS -> SocialExportPresets.youtubeShorts(projectId, outputFileName)
            ExportPresetType.TIKTOK -> SocialExportPresets.tiktok(projectId, outputFileName)
            ExportPresetType.INSTAGRAM_REELS -> SocialExportPresets.instagramReels(projectId, outputFileName)
            ExportPresetType.FACEBOOK -> SocialExportPresets.facebook(projectId, outputFileName)
            ExportPresetType.CUSTOM -> com.example.timeline.export.model.ExportSettings(
                exportId = java.util.UUID.randomUUID().toString(),
                projectId = projectId,
                outputFileName = outputFileName,
                outputDirectoryUri = "",
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                frameRate = com.example.timeline.export.model.ExportFrameRate.FPS_30,
                codec = com.example.timeline.export.model.ExportCodec.H264,
                container = com.example.timeline.export.model.ExportContainer.MP4,
                videoBitrate = 10_000_000,
                maxBitrate = 15_000_000,
                bitrateMode = com.example.timeline.export.model.ExportSettings.BitrateMode.VBR,
                keyframeInterval = 2,
                qualityPreset = com.example.timeline.export.model.ExportQualityPreset.CUSTOM,
                audioSampleRate = 48000,
                audioChannels = 2,
                audioBitrate = 320_000
            )
        }
    }
}
