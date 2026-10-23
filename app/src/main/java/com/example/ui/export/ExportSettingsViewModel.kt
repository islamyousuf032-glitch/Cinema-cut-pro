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

    private val _uiState = MutableStateFlow(ExportSettingsUiState())
    val uiState: StateFlow<ExportSettingsUiState> = _uiState.asStateFlow()

    fun initProject(project: TimelineProject) {
        val initialSettings = ExportSettingsDefaults.adaptToSimpleSource(
            ExportPresetManager.getSettingsForPreset(
                ExportPresetType.YOUTUBE_1080P,
                project.id,
                "ChromaPro_${project.name}_Export"
            ),
            project,
            matchProjectOrientation = true
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
                    ExportSettingsDefaults.adaptToSimpleSource(
                        it,
                        project,
                        matchProjectOrientation = state.selectedPreset !in setOf(
                            ExportPresetType.YOUTUBE_SHORTS,
                            ExportPresetType.TIKTOK,
                            ExportPresetType.INSTAGRAM_REELS,
                            ExportPresetType.CUSTOM
                        )
                    )
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
        val currentSettings = _uiState.value.currentSettings
        val requestedSettings = ExportPresetManager.getSettingsForPreset(
            preset,
            project.id,
            currentSettings?.outputFileName ?: "CinemaCutPro_Export"
        ).copy(outputDirectoryUri = currentSettings?.outputDirectoryUri.orEmpty())
        val newSettings = ExportSettingsDefaults.adaptToSimpleSource(
            requestedSettings,
            project,
            matchProjectOrientation = preset !in setOf(
                ExportPresetType.YOUTUBE_SHORTS,
                ExportPresetType.TIKTOK,
                ExportPresetType.INSTAGRAM_REELS
            )
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
        // The exact backend also validates again in the foreground worker. Do not combine generic
        // codec/HDR/alpha rules here: they can advertise support that the selected backend lacks.
        val backendVal = state.project?.let { backendFactory.validateWithAvailableBackend(settings, it) }
        val validationResult = backendVal ?: ExportValidationResult(
            isValid = false,
            unsupportedFeatures = listOf("Select a project before exporting."),
            warnings = emptyList(),
            errorMessage = "Select a project before exporting."
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
        val includesEmbeddedAudio = settings.includeAudio && activeVideoTrack != null && !activeVideoTrack.isMuted &&
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
