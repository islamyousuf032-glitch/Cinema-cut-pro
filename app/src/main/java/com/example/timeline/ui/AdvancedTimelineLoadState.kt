package com.example.timeline.ui

enum class AdvancedTimelineLoadState(val message: String) {
    STARTING("Starting initialization..."),
    LOCKING_ORIENTATION("Locking orientation..."),
    LOADING_PROJECT("Loading project data..."),
    LOADING_TIMELINE("Loading timeline configuration..."),
    PREPARING_CLIP_LAYOUT("Preparing clip layout..."),
    PREPARING_THUMBNAILS("Caching thumbnails..."),
    PREPARING_MINI_VIEWPORT("Preparing mini viewport..."),
    READY("Ready"),
    FAILED("Failed")
}
