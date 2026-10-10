package com.example.timeline.engine.preview

import android.view.Surface
import com.example.model.adjustments.VideoAdjustmentParams
import kotlinx.coroutines.flow.StateFlow

enum class PreviewEngineType(val displayName: String) {
    AUTO("Auto"),
    VLC_NATIVE("Native VLC"),
    MEDIA3_FALLBACK("Media3"),
    BROWSER("WebView/Browser"),
    STILL_FRAME("Still Frame"),
    PROXY_PREVIEW("Proxy Preview"),
    NATIVE_CPP("Native C++")
}

enum class PreviewState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    ERROR
}

interface PreviewSource {
    val uri: String
    val isProxy: Boolean
}

data class PreviewEngineCapabilities(
    val canPlayMovingVideo: Boolean,
    val canScrub: Boolean,
    val canRenderFirstFrame: Boolean,
    val canApplyRealtimeColorGrade: Boolean,
    val canApplyBasicAdjustments: Boolean,
    val canUseProxy: Boolean,
    val supports10Bit: Boolean = false,
    val supportsHDR: Boolean = false,
    val supports4K: Boolean = true,
    val currentFailureReason: String? = null
)

interface PreviewEngine {
    val engineType: PreviewEngineType
    val capabilities: PreviewEngineCapabilities

    val currentState: StateFlow<PreviewState>
    val currentError: StateFlow<String?>
    val firstFrameRendered: StateFlow<Boolean>
    
    fun setSurface(surface: Surface?)
    fun loadMedia(uriString: String, useProxy: Boolean, sourcePositionMs: Long, evaluatedParams: VideoAdjustmentParams?, presentationTimeUs: Long)
    fun play()
    fun pause()
    fun seekTo(timeUs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setResolutionFallback(resolutionMode: String)
    fun release()
    
    val currentPositionUs: Long
    val durationUs: Long
    val isPlaying: Boolean
    
    // Add callback for time updates
    var onTimeChanged: ((Long) -> Unit)?
}
