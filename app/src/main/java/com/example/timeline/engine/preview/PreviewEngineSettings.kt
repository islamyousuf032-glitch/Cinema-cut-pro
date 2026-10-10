package com.example.timeline.engine.preview

data class PreviewEngineSettings(
    val selectedEngine: PreviewEngineType = PreviewEngineType.AUTO,
    val qualityMode: PreviewQualityMode = PreviewQualityMode.AUTO,
    val previewMode: PreviewMode = PreviewMode.SMOOTH_PLAYBACK,
    val showDebugOverlay: Boolean = false,
    val showEngineBadge: Boolean = true,
    val autoFallbackOnFailure: Boolean = true,
    val autoGenerateProxy: Boolean = false
)

enum class PreviewMode(val displayName: String) {
    SMOOTH_PLAYBACK("Smooth Playback"),
    COLOR_ACCURATE("Color Accurate Preview"),
    GRADED_STILL("Graded Still Preview"),
    PROXY_PREVIEW("Proxy Preview")
}
