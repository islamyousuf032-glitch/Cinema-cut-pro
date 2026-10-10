package com.example.timeline.ui.viewport

enum class ViewportState {
    EMPTY_PROJECT,
    NO_CLIP_AT_PLAYHEAD,
    LOADING_MEDIA,
    READY,
    PLAYING,
    PAUSED,
    UNSUPPORTED_MEDIA,
    PROXY_REQUIRED,
    ERROR
}

enum class FitMode {
    FIT_INSIDE,
    FILL_CROP,
    STRETCH
}

data class ViewportUiState(
    val viewportState: ViewportState = ViewportState.EMPTY_PROJECT,
    val errorMessage: String? = null,
    val showSafeArea: Boolean = true,
    val showGuideOverlay: Boolean = false,
    val fitMode: FitMode = FitMode.FIT_INSIDE
)
