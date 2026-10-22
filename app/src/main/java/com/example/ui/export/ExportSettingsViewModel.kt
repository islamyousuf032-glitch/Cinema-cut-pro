package com.example.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.backend.ExportBackendFactory
import com.example.timeline.export.model.*
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
        val initialSettings = ExportSettingsDefaults.adaptToSimpleSource(
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

    /** Keeps an open export dialog bound to timeline edits without resetting its chosen settings. */
    fun syncProject(project: TimelineProject) {
        if (_uiState.value.project?.id != project.id) {
            initProject(project)
            return
        }
        if (_uiState.value.project != project) {
            _uiState.update { state ->
                val adaptedSettings = state.currentSettings?.let {
                    ExportSettingsDefaults.adaptToSimpleSource(it, project)
                }
                state.copy(project = project, currentSettings = adaptedSettings)
            }
            validateSettings()
        }
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
        val newSettings = ExportSettingsDefaults.adaptToSimpleSource(
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
        val enabledVideoClips = project?.tracks
            ?.filter { it.type == com.example.timeline.core.TrackType.VIDEO && it.isVisible }
            ?.flatMap { track -> track.clips.filter { it.isEnabled } }
            .orEmpty()
        val totalEnabledDurationFrames = runCatching {
            enabledVideoClips.fold(0L) { total, clip -> Math.addExact(total, clip.duration) }
        }.getOrNull() ?: 0L
        val projectFps = project?.settings?.getFpsRational()?.toDouble() ?: 0.0
        val durationSec = if (totalEnabledDurationFrames > 0L && projectFps > 0.0) {
            totalEnabledDurationFrames / projectFps
        } else {
            0.0
        }
        val activeVideoTrack = project?.tracks
            ?.filter { it.type == com.example.timeline.core.TrackType.VIDEO && it.isVisible && it.clips.any { clip -> clip.isEnabled } }
            ?.singleOrNull()
        val includesEmbeddedAudio = activeVideoTrack != null && !activeVideoTrack.isMuted &&
            enabledVideoClips.all { clip ->
                project?.mediaAssets?.firstOrNull { it.assetId == clip.mediaId }?.metadata?.hasAudio == true
            }
        val estimatedBitrate = settings.videoBitrate.toDouble() +
            if (includesEmbeddedAudio) settings.audioBitrate.toDouble() else 0.0
        val estimatedSizeMb: Float = if (validationResult.isValid) {
            (estimatedBitrate * durationSec / (8.0 * 1024.0 * 1024.0)).toFloat()
        } else {
            0f
        }
        val estimatedRenderTimeSec = if (validationResult.isValid) (durationSec * 2).toLong() else 0L

        _uiState.update {
            it.copy(
                validationResult = validationResult,
                estimatedSizeMb = estimatedSizeMb,
                estimatedRenderTimeSec = estimatedRenderTimeSec
            )
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
