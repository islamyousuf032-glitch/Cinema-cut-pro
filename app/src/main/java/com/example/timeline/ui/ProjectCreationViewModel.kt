package com.example.timeline.ui

import androidx.lifecycle.ViewModel
import com.example.timeline.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ResolutionOption(val width: Int, val height: Int) {
    val display: String get() = "${width}x${height}"
}

data class CreateProjectUiState(
    val projectName: String = "New Project",
    val aspectRatio: ProjectAspectRatioPreset = ProjectAspectRatioPreset.W_16_9,
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val resolutionOptions: List<ResolutionOption> = listOf(
        ResolutionOption(1280, 720),
        ResolutionOption(1920, 1080),
        ResolutionOption(2560, 1440),
        ResolutionOption(3840, 2160)
    ),
    val frameRatePreset: ProjectFrameRatePreset = ProjectFrameRatePreset.FPS_24,
    val colorSpace: ProjectColorSpace = ProjectColorSpace.REC_709,
    val audioSampleRate: ProjectAudioSampleRate = ProjectAudioSampleRate.HZ_48000
) {
    val isValid: Boolean get() = projectName.isNotBlank() && resolutionWidth > 0 && resolutionHeight > 0
}

class ProjectCreationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    fun setProjectName(name: String) {
        _uiState.update { it.copy(projectName = name) }
    }

    fun setAspectRatio(ratio: ProjectAspectRatioPreset) {
        val options = getResolutionOptionsForRatio(ratio)
        val defaultRes = options.getOrNull(1) ?: options.firstOrNull() ?: ResolutionOption(1920, 1080)
        _uiState.update { 
            it.copy(
                aspectRatio = ratio,
                resolutionOptions = options,
                resolutionWidth = defaultRes.width,
                resolutionHeight = defaultRes.height
            ) 
        }
    }

    fun setResolution(width: Int, height: Int) {
        _uiState.update { it.copy(resolutionWidth = width, resolutionHeight = height) }
    }

    fun setFrameRate(frameRate: ProjectFrameRatePreset) {
        _uiState.update { it.copy(frameRatePreset = frameRate) }
    }

    fun setColorSpace(colorSpace: ProjectColorSpace) {
        _uiState.update { it.copy(colorSpace = colorSpace) }
    }

    fun setAudioSampleRate(sampleRate: ProjectAudioSampleRate) {
        _uiState.update { it.copy(audioSampleRate = sampleRate) }
    }

    private fun getResolutionOptionsForRatio(ratio: ProjectAspectRatioPreset): List<ResolutionOption> {
        return when (ratio) {
            ProjectAspectRatioPreset.W_16_9 -> listOf(
                ResolutionOption(1280, 720),
                ResolutionOption(1920, 1080),
                ResolutionOption(2560, 1440),
                ResolutionOption(3840, 2160),
                ResolutionOption(7680, 4320)
            )
            ProjectAspectRatioPreset.W_9_16 -> listOf(
                ResolutionOption(720, 1280),
                ResolutionOption(1080, 1920),
                ResolutionOption(1440, 2560),
                ResolutionOption(2160, 3840)
            )
            ProjectAspectRatioPreset.W_1_1 -> listOf(
                ResolutionOption(1080, 1080),
                ResolutionOption(1440, 1440),
                ResolutionOption(2160, 2160)
            )
            // Fallback for others
            else -> listOf(
                ResolutionOption(1920, 1080),
                ResolutionOption(1080, 1920)
            )
        }
    }
}
