package com.example.timeline.engine.preview

data class PreviewPlayerState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val usingProxy: Boolean = false,
    val bufferedPercent: Int = 0
)
