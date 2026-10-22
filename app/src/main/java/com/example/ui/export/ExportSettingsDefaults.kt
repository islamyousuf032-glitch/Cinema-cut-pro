package com.example.ui.export

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TrackType
import com.example.timeline.export.model.ExportAudioSettings
import com.example.timeline.export.model.ExportColorSettings
import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import com.example.timeline.export.model.ExportFrameRate
import com.example.timeline.export.model.ExportQualityPreset
import com.example.timeline.export.model.ExportSettings
import com.example.timeline.media.FrameRate
import com.example.timeline.media.MediaAsset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Defaults and UI mappings for the currently implemented Media3 H.264/MP4 export path. */
internal object ExportSettingsDefaults {
    private data class ActiveSource(val asset: MediaAsset)

    fun adaptToSimpleSource(settings: ExportSettings, project: TimelineProject): ExportSettings {
        val activeSources = project.tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .flatMap { track -> track.clips.filter { it.isEnabled }.mapNotNull { clip ->
                project.mediaAssets.firstOrNull { it.assetId == clip.mediaId }?.let(::ActiveSource)
            } }

        val sourceRate = commonConstantFrameRate(activeSources)
        val projectRate = project.settings.getFpsRational()
            .takeIf { it.numerator > 0 && it.denominator > 0 }
            ?.let { ExportFrameRate(it.numerator, it.denominator) }
        val requestedRate = sourceRate ?: projectRate ?: settings.frameRate

        val commonAudio = commonEmbeddedAudioFormat(activeSources)
        val requestedMode = when (settings.bitrateMode) {
            ExportSettings.BitrateMode.CRF,
            ExportSettings.BitrateMode.AUTO -> ExportSettings.BitrateMode.VBR
            else -> settings.bitrateMode
        }
        val safeVideoBitrate = settings.videoBitrate.coerceAtLeast(1)

        return settings.copy(
            frameRate = requestedRate,
            codec = ExportCodec.H264,
            container = ExportContainer.MP4,
            bitrateMode = requestedMode,
            maxBitrate = safeVideoBitrate,
            useHardwareEncoder = true,
            useOriginalMedia = true,
            allowProxyExport = false,
            renderAlpha = false,
            exportHDR = false,
            colorOutput = ExportColorSettings.ColorOutput.Rec709,
            toneMappingMode = ExportColorSettings.ToneMappingMode.STANDARD,
            audioCodec = ExportAudioSettings.AudioCodec.AAC,
            audioSampleRate = commonAudio?.first ?: settings.audioSampleRate.coerceAtLeast(1),
            audioChannels = commonAudio?.second ?: settings.audioChannels.coerceIn(1, 2),
            normalizeAudio = false,
            includeWatermark = false,
            watermarkSettings = settings.watermarkSettings?.copy(enabled = false)
        )
    }

    fun bitrateForQualityPreset(
        preset: ExportQualityPreset,
        width: Int,
        height: Int,
        frameRate: ExportFrameRate
    ): Int? {
        val multiplier = when (preset) {
            ExportQualityPreset.DRAFT -> 0.45
            ExportQualityPreset.STANDARD -> 0.7
            ExportQualityPreset.HIGH -> 1.0
            ExportQualityPreset.MASTER -> 1.5
            ExportQualityPreset.CUSTOM -> return null
        }
        val pixelsScale = width.coerceAtLeast(1).toDouble() * height.coerceAtLeast(1) /
            (1920.0 * 1080.0)
        val frameRateScale = (frameRate.floatValue.takeIf { it.isFinite() && it > 0f } ?: 30f) / 30.0
        return (8_000_000.0 * pixelsScale * frameRateScale * multiplier)
            .roundToInt()
            .coerceIn(500_000, 150_000_000)
    }

    fun closestExportFrameRate(source: FrameRate): ExportFrameRate {
        val commonRates = listOf(
            ExportFrameRate.FPS_23_976, ExportFrameRate.FPS_24, ExportFrameRate.FPS_25,
            ExportFrameRate.FPS_29_97, ExportFrameRate.FPS_30, ExportFrameRate.FPS_50,
            ExportFrameRate.FPS_59_94, ExportFrameRate.FPS_60, ExportFrameRate.FPS_120
        )
        val nearest = commonRates.minByOrNull { abs(it.floatValue - source.fpsAsFloat) }
        return if (nearest != null && abs(nearest.floatValue - source.fpsAsFloat) <= 0.01f) {
            nearest
        } else {
            ExportFrameRate(source.numerator, source.denominator)
        }
    }

    private fun commonConstantFrameRate(sources: List<ActiveSource>): ExportFrameRate? {
        if (sources.isEmpty()) return null
        val rates = sources.map { source ->
            val metadata = source.asset.metadata
            if (metadata.isVariableFrameRate) return null
            (metadata.exactFrameRate ?: metadata.estimatedFrameRate)
                ?.takeIf { it.numerator > 0 && it.denominator > 0 }
                ?: return null
        }
        val first = rates.first()
        if (rates.any { abs(it.fpsAsFloat - first.fpsAsFloat) > 0.01f }) return null
        return closestExportFrameRate(first)
    }

    private fun commonEmbeddedAudioFormat(sources: List<ActiveSource>): Pair<Int, Int>? {
        if (sources.isEmpty() || sources.any { !it.asset.metadata.hasAudio }) return null
        val streams = sources.map { it.asset.metadata.audioStreams.singleOrNull() ?: return null }
        val first = streams.first()
        if (first.sampleRate <= 0 || first.channelCount !in 1..2) return null
        if (streams.any { it.sampleRate != first.sampleRate || it.channelCount != first.channelCount }) return null
        return first.sampleRate to first.channelCount
    }
}
