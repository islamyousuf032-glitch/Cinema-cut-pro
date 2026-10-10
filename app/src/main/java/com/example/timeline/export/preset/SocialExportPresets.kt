package com.example.timeline.export.preset

import com.example.timeline.export.model.ExportSettings
import com.example.timeline.export.model.ExportFrameRate
import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import com.example.timeline.export.model.ExportQualityPreset
import java.util.UUID

object SocialExportPresets {
    fun youtube1080p(projectId: String, outputFileName: String): ExportSettings {
        return ExportSettings(
            exportId = UUID.randomUUID().toString(),
            projectId = projectId,
            outputFileName = outputFileName,
            outputDirectoryUri = "",
            resolutionWidth = 1920,
            resolutionHeight = 1080,
            frameRate = ExportFrameRate.FPS_30,
            codec = ExportCodec.H264,
            container = ExportContainer.MP4,
            videoBitrate = 8_000_000,
            maxBitrate = 12_000_000,
            bitrateMode = ExportSettings.BitrateMode.VBR,
            keyframeInterval = 2,
            qualityPreset = ExportQualityPreset.HIGH,
            audioSampleRate = 48000,
            audioChannels = 2,
            audioBitrate = 384_000
        )
    }

    fun youtube4K(projectId: String, outputFileName: String): ExportSettings {
        return ExportSettings(
            exportId = UUID.randomUUID().toString(),
            projectId = projectId,
            outputFileName = outputFileName,
            outputDirectoryUri = "",
            resolutionWidth = 3840,
            resolutionHeight = 2160,
            frameRate = ExportFrameRate.FPS_60,
            codec = ExportCodec.H265,
            container = ExportContainer.MP4,
            videoBitrate = 45_000_000,
            maxBitrate = 60_000_000,
            bitrateMode = ExportSettings.BitrateMode.VBR,
            keyframeInterval = 2,
            qualityPreset = ExportQualityPreset.MASTER,
            audioSampleRate = 48000,
            audioChannels = 2,
            audioBitrate = 384_000
        )
    }

    fun youtubeShorts(projectId: String, outputFileName: String): ExportSettings {
        return ExportSettings(
            exportId = UUID.randomUUID().toString(),
            projectId = projectId,
            outputFileName = outputFileName,
            outputDirectoryUri = "",
            resolutionWidth = 1080,
            resolutionHeight = 1920,
            frameRate = ExportFrameRate.FPS_30,
            codec = ExportCodec.H264,
            container = ExportContainer.MP4,
            videoBitrate = 8_000_000,
            maxBitrate = 12_000_000,
            bitrateMode = ExportSettings.BitrateMode.VBR,
            keyframeInterval = 2,
            qualityPreset = ExportQualityPreset.HIGH,
            audioSampleRate = 48000,
            audioChannels = 2,
            audioBitrate = 256_000
        )
    }

    fun tiktok(projectId: String, outputFileName: String): ExportSettings {
        return ExportSettings(
            exportId = UUID.randomUUID().toString(),
            projectId = projectId,
            outputFileName = outputFileName,
            outputDirectoryUri = "",
            resolutionWidth = 1080,
            resolutionHeight = 1920,
            frameRate = ExportFrameRate.FPS_30,
            codec = ExportCodec.H264,
            container = ExportContainer.MP4,
            videoBitrate = 8_000_000,
            maxBitrate = 10_000_000,
            bitrateMode = ExportSettings.BitrateMode.VBR,
            keyframeInterval = 2,
            qualityPreset = ExportQualityPreset.HIGH,
            audioSampleRate = 44100,
            audioChannels = 2,
            audioBitrate = 256_000
        )
    }

    fun instagramReels(projectId: String, outputFileName: String): ExportSettings {
        return ExportSettings(
            exportId = UUID.randomUUID().toString(),
            projectId = projectId,
            outputFileName = outputFileName,
            outputDirectoryUri = "",
            resolutionWidth = 1080,
            resolutionHeight = 1920,
            frameRate = ExportFrameRate.FPS_30,
            codec = ExportCodec.H264,
            container = ExportContainer.MP4,
            videoBitrate = 8_000_000,
            maxBitrate = 10_000_000,
            bitrateMode = ExportSettings.BitrateMode.VBR,
            keyframeInterval = 2,
            qualityPreset = ExportQualityPreset.HIGH,
            audioSampleRate = 44100,
            audioChannels = 2,
            audioBitrate = 256_000
        )
    }

    fun facebook(projectId: String, outputFileName: String): ExportSettings {
        return ExportSettings(
            exportId = UUID.randomUUID().toString(),
            projectId = projectId,
            outputFileName = outputFileName,
            outputDirectoryUri = "",
            resolutionWidth = 1920,
            resolutionHeight = 1080,
            frameRate = ExportFrameRate.FPS_30,
            codec = ExportCodec.H264,
            container = ExportContainer.MP4,
            videoBitrate = 4_000_000,
            maxBitrate = 8_000_000,
            bitrateMode = ExportSettings.BitrateMode.VBR,
            keyframeInterval = 2,
            qualityPreset = ExportQualityPreset.STANDARD,
            audioSampleRate = 44100,
            audioChannels = 2,
            audioBitrate = 128_000
        )
    }
}
