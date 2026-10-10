package com.example.ui.editor

import androidx.lifecycle.ViewModel
import com.example.timeline.engine.preview.PreviewEngineSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.timeline.engine.preview.PreviewEngineType
import com.example.timeline.engine.preview.PreviewQualityMode
import com.example.timeline.engine.preview.PreviewMode

class EditorViewModel : ViewModel() {
    private val _previewSettings = MutableStateFlow(PreviewEngineSettings())
    val previewSettings: StateFlow<PreviewEngineSettings> = _previewSettings.asStateFlow()

    private val _playheadFrame = MutableStateFlow(0L)
    val playheadFrame: StateFlow<Long> = _playheadFrame.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    fun updatePlayheadFrame(frame: Long) {
        _playheadFrame.value = Math.max(0, frame)
    }

    fun selectClip(id: String?) {
        _selectedClipId.value = id
    }

    fun updateSelectedEngine(engine: PreviewEngineType) {
        _previewSettings.update { it.copy(selectedEngine = engine) }
    }

    fun updateQualityMode(quality: PreviewQualityMode) {
        _previewSettings.update { it.copy(qualityMode = quality) }
    }

    fun updatePreviewMode(mode: PreviewMode) {
        _previewSettings.update { it.copy(previewMode = mode) }
    }

    fun toggleDebugOverlay() {
        _previewSettings.update { it.copy(showDebugOverlay = !it.showDebugOverlay) }
    }

    fun toggleEngineBadge() {
        _previewSettings.update { it.copy(showEngineBadge = !it.showEngineBadge) }
    }

    fun toggleAutoFallback() {
        _previewSettings.update { it.copy(autoFallbackOnFailure = !it.autoFallbackOnFailure) }
    }

    fun toggleAutoGenerateProxy() {
        _previewSettings.update { it.copy(autoGenerateProxy = !it.autoGenerateProxy) }
    }
}
