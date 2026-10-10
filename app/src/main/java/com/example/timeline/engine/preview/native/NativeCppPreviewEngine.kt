package com.example.timeline.engine.preview.native

import android.view.Surface
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.engine.preview.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class NativeCppPreviewEngine : PreviewEngine {
    override val engineType = PreviewEngineType.NATIVE_CPP

    override val capabilities: PreviewEngineCapabilities = PreviewEngineCapabilities(
        canPlayMovingVideo = true,
        canScrub = true,
        canRenderFirstFrame = true,
        canApplyRealtimeColorGrade = true, // We assume custom OpenGL pipeline natively
        canApplyBasicAdjustments = true,
        canUseProxy = true,
        currentFailureReason = null
    )
    
    private val _currentState = MutableStateFlow(PreviewState.IDLE)
    override val currentState: StateFlow<PreviewState> = _currentState.asStateFlow()
    
    private val _currentError = MutableStateFlow<String?>(null)
    override val currentError: StateFlow<String?> = _currentError.asStateFlow()
    
    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()
    
    private var nativeHandle: Long = 0
    private var surface: Surface? = null
    
    override var onTimeChanged: ((Long) -> Unit)? = null

    private var timeTrackerJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    
    init {
        nativeHandle = NativePlayerBridge.nativeCreate(null)
    }

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        // Ideally we pass this to native
    }

    override fun loadMedia(
        uriString: String,
        useProxy: Boolean,
        sourcePositionMs: Long,
        evaluatedParams: VideoAdjustmentParams?,
        presentationTimeUs: Long,
        forceSeek: Boolean
    ) {
        _currentState.value = PreviewState.LOADING
        _currentError.value = null
        
        val path = if(uriString.startsWith("file://")) uriString.substring(7) else uriString
        
        val res = NativePlayerBridge.nativeOpen(nativeHandle, path)
        if (res < 0) {
            val err = NativePlayerBridge.nativeGetLastError(nativeHandle)
            _currentError.value = err
            _currentState.value = PreviewState.ERROR
        } else {
            NativePlayerBridge.nativeSeek(nativeHandle, sourcePositionMs * 1000)
            _currentState.value = PreviewState.READY
        }
    }

    override fun play() {
        if (_currentState.value == PreviewState.READY || _currentState.value == PreviewState.PLAYING) {
            NativePlayerBridge.nativePlay(nativeHandle)
            _currentState.value = PreviewState.PLAYING
            if (timeTrackerJob?.isActive != true) {
                timeTrackerJob = scope.launch {
                    while (isActive && _currentState.value == PreviewState.PLAYING) {
                        onTimeChanged?.invoke(NativePlayerBridge.nativeGetPositionUs(nativeHandle))
                        delay(1000 / 30)
                    }
                }
            }
        }
    }

    override fun pause() {
        NativePlayerBridge.nativePause(nativeHandle)
        _currentState.value = PreviewState.READY
        timeTrackerJob?.cancel()
        timeTrackerJob = null
    }

    override fun seekTo(timeUs: Long) {
        NativePlayerBridge.nativeSeek(nativeHandle, timeUs)
    }

    override fun setPlaybackSpeed(speed: Float) {
    }

    override fun setResolutionFallback(resolutionMode: String) {
    }

    override fun release() {
        NativePlayerBridge.nativeRelease(nativeHandle)
        nativeHandle = 0
    }

    override val currentPositionUs: Long
        get() = NativePlayerBridge.nativeGetPositionUs(nativeHandle)
        
    override val durationUs: Long
        get() = NativePlayerBridge.nativeGetDurationUs(nativeHandle)
        
    override val isPlaying: Boolean
        get() = _currentState.value == PreviewState.PLAYING
}
