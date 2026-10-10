package com.example.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.backend.ExportBackendFactory
import com.example.timeline.export.model.*
import com.example.timeline.media.FrameRate
import kotlin.math.abs
import com.example.timeline.export.preset.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ExportSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val backendFactory = ExportBackendFactory(application)
    private val diagnostics = backendFactory.getDiagnostics()
    private val mediaCodecCaps = diagnostics.mediaCodecCapabilities
    private val ffmpegCaps = diagnostics.ffmpegCapabilities

    private val _uiState = MutableStateFlow(ExportSettingsUiState())
    val uiState: StateFlow<ExportSettingsUiState> = _uiState.asStateFlow()

    fun initProject(project: TimelineProject) {
        val initialSettings = adaptToSimpleSource(
            ExportPresetManager.getSettingsForPreset(
                ExportPresetType.YOUTUBE_1080P,
                project.id,
                "ChromaPro_${project.name}_Export"
            ),
            project
        )
        
        _uiState.update { 
            it.copy(
                project = project,
                currentSettings = initialSettings,
                selectedPreset = ExportPresetType.YOUTUBE_1080P
            )
        }
        validateSettings()
    }

    fun updateSettings(updater: (ExportSettings) -> ExportSettings) {
        _uiState.update {
            val newSettings = updater(it.currentSettings ?: return@update it)
            it.copy(
                currentSettings = newSettings,
                selectedPreset = ExportPresetType.CUSTOM
            )
        }
        validateSettings()
    }

    fun selectPreset(preset: ExportPresetType) {
        val project = _uiState.value.project ?: return
        val newSettings = adaptToSimpleSource(
            ExportPresetManager.getSettingsForPreset(
                preset,
                project.id,
                _uiState.value.currentSettings?.outputFileName ?: "output"
            ),
            project
        )
        _uiState.update {
            it.copy(
                currentSettings = newSettings,
                selectedPreset = preset
            )
        }
        validateSettings()
    }

    private fun validateSettings() {
        val state = _uiState.value
        val settings = state.currentSettings ?: return
        val codecVal = ExportCodecValidator.validate(settings.codec, settings.container, mediaCodecCaps, ffmpegCaps)
        val alphaVal = AlphaExportValidator.validate(settings.renderAlpha, settings.codec, settings.container, ffmpegCaps)
        val hdrVal = HdrExportValidator.validate(settings.exportHDR, settings.colorOutput, settings.codec, mediaCodecCaps, ffmpegCaps)
        val backendVal = state.project?.let { backendFactory.validateWithAvailableBackend(settings, it) }

        val allErrors = codecVal.unsupportedFeatures + alphaVal.unsupportedFeatures + hdrVal.unsupportedFeatures +
            backendVal?.unsupportedFeatures.orEmpty()
        val allWarnings = codecVal.warnings + alphaVal.warnings + hdrVal.warnings + backendVal?.warnings.orEmpty()
        val validationResult = ExportValidationResult(
            isValid = allErrors.isEmpty(),
            unsupportedFeatures = allErrors.distinct(),
            warnings = allWarnings.distinct(),
            errorMessage = allErrors.firstOrNull()
        )

        val project = state.project
        val maxEndFrame = project?.tracks?.flatMap { it.clips }?.maxOfOrNull { it.timelineEnd } ?: 0L
        val projectFps = project?.settings?.getFpsRational()?.toDouble() ?: 0.0
        val durationSec = if (maxEndFrame > 0L && projectFps > 0.0) maxEndFrame / projectFps else 0.0
        val estimatedSizeMb = (settings.videoBitrate + settings.audioBitrate) * durationSec / (8 * 1024 * 1024f)

        _uiState.update {
            it.copy(
                validationResult = validationResult,
                estimatedSizeMb = estimatedSizeMb.toFloat(),
                estimatedRenderTimeSec = (durationSec * 2).toLong()
            )
        }
    }

    /**
     * The initial preset follows the one visible source clip where possible. Media3 cannot request
     * an arbitrary output frame rate or resample the audio format in this bounded first adapter.
     */
    private fun adaptToSimpleSource(settings: ExportSettings, project: TimelineProject): ExportSettings {
        val activeClips = project.tracks
            .filter { it.type == com.example.timeline.core.TrackType.VIDEO && it.isVisible }
            .flatMap { track -> track.clips.filter { it.isEnabled }.map { track to it } }
        if (activeClips.size != 1) return settings

        val (track, clip) = activeClips.single()
        val asset = project.mediaAssets.firstOrNull { it.assetId == clip.mediaId } ?: return settings
        val sourceRate = asset.metadata.exactFrameRate ?: asset.metadata.estimatedFrameRate
        val exportRate = sourceRate?.takeIf { it.numerator > 0 && it.denominator > 0 }?.let(::closestExportFrameRate)
            ?: settings.frameRate
        val audio = asset.metadata.audioStreams.singleOrNull()

        return settings.copy(
            frameRate = exportRate,
            audioSampleRate = audio?.sampleRate ?: settings.audioSampleRate,
            audioChannels = audio?.channelCount ?: settings.audioChannels
        )
    }

    private fun closestExportFrameRate(source: FrameRate): ExportFrameRate {
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
}

data class ExportSettingsUiState(
    val project: TimelineProject? = null,
    val currentSettings: ExportSettings? = null,
    val selectedPreset: ExportPresetType = ExportPresetType.YOUTUBE_1080P,
    val validationResult: ExportValidationResult = ExportValidationResult(true, emptyList(), emptyList(), null),
    val estimatedSizeMb: Float = 0f,
    val estimatedRenderTimeSec: Long = 0L
)
