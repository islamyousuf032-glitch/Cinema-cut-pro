package com.example.timeline.engine.preview

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.adjustments.engine.VideoEffectGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import com.example.model.adjustments.VideoAdjustmentParams

class Media3FallbackPreviewEngine(context: Context) : PreviewEngine {
    override val engineType = PreviewEngineType.MEDIA3_FALLBACK

    override val capabilities: PreviewEngineCapabilities = PreviewEngineCapabilities(
        canPlayMovingVideo = true,
        canScrub = true,
        canRenderFirstFrame = true,
        canApplyRealtimeColorGrade = true, // Media3 with custom ExoPlayer video effects
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
    
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val effectGraph = VideoEffectGraph(context)
    
    // For legacy compat in codebase
    private val _state = MutableStateFlow(PreviewPlayerState())
    val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

    private var currentUri: String? = null

    override var onTimeChanged: ((Long) -> Unit)? = null

    private var timeTrackerJob: kotlinx.coroutines.Job? = null
    private var pausedGradeRefreshJob: kotlinx.coroutines.Job? = null
    private var pausedGradeRefreshPositionMs = 0L
    private var lastPausedGradeRefreshMs = 0L
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.d("PLAYER_MEDIA3", "onRenderedFirstFrame called")
                _firstFrameRendered.value = true
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(
                    isReady = playbackState == Player.STATE_READY,
                    error = if (playbackState == Player.STATE_IDLE && exoPlayer.playerError != null) {
                        exoPlayer.playerError?.message ?: "Playback Error"
                    } else it.error
                )}
                when (playbackState) {
                    Player.STATE_BUFFERING -> _currentState.value = PreviewState.LOADING
                    Player.STATE_READY -> _currentState.value = if(exoPlayer.isPlaying) PreviewState.PLAYING else PreviewState.READY
                    Player.STATE_ENDED -> {
                        // Emit the final position once; ExoPlayer stops its 30fps tracker as soon
                        // as isPlaying becomes false, but the timeline may still have another clip.
                        _currentState.value = PreviewState.PLAYING
                        onTimeChanged?.invoke(exoPlayer.currentPosition * 1000L)
                        _currentState.value = PreviewState.READY
                    }
                    Player.STATE_IDLE -> {
                        if (exoPlayer.playerError != null) {
                            _currentError.value = exoPlayer.playerError?.message
                            _currentState.value = PreviewState.ERROR
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    cancelPausedGradeRefresh()
                    _currentState.value = PreviewState.PLAYING
                    startTimeTracker()
                } else {
                    if (exoPlayer.playbackState == Player.STATE_READY) _currentState.value = PreviewState.READY
                    stopTimeTracker()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.update { it.copy(error = error.message) }
                _currentError.value = error.message
                _currentState.value = PreviewState.ERROR
                stopTimeTracker()
            }
        })
        try {
            exoPlayer.setVideoEffects(effectGraph.getEffects())
            android.util.Log.d("PLAYER_MEDIA3", "Enabled setVideoEffects for real-time color grading")
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startTimeTracker() {
        if (timeTrackerJob?.isActive == true) return
        timeTrackerJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                onTimeChanged?.invoke(exoPlayer.currentPosition * 1000L)
                delay(1000 / 30) // 30fps
            }
        }
    }

    private fun stopTimeTracker() {
        timeTrackerJob?.cancel()
        timeTrackerJob = null
    }

    private fun cancelPausedGradeRefresh() {
        pausedGradeRefreshJob?.cancel()
        pausedGradeRefreshJob = null
    }

    private fun requestPausedGradeRefresh(positionMs: Long) {
        pausedGradeRefreshPositionMs = positionMs
        val now = android.os.SystemClock.elapsedRealtime()
        val waitMs = (33L - (now - lastPausedGradeRefreshMs)).coerceAtLeast(0L)
        if (waitMs == 0L) {
            cancelPausedGradeRefresh()
            if (!exoPlayer.isPlaying) {
                exoPlayer.seekTo(pausedGradeRefreshPositionMs)
                lastPausedGradeRefreshMs = now
            }
            return
        }

        cancelPausedGradeRefresh()
        pausedGradeRefreshJob = scope.launch {
            delay(waitMs)
            pausedGradeRefreshJob = null
            if (!exoPlayer.isPlaying) {
                exoPlayer.seekTo(pausedGradeRefreshPositionMs)
                lastPausedGradeRefreshMs = android.os.SystemClock.elapsedRealtime()
            }
        }
    }
    
    override fun setSurface(surface: Surface?) {
        exoPlayer.setVideoSurface(surface)
    }

    override fun loadMedia(
        uriString: String,
        useProxy: Boolean,
        sourcePositionMs: Long,
        evaluatedParams: VideoAdjustmentParams?,
        presentationTimeUs: Long,
        forceSeek: Boolean
    ) {
        android.util.Log.d("PLAYER_MEDIA3", "Loading media uri: $uriString at $sourcePositionMs ms. Using proxy: $useProxy")
        
        if (evaluatedParams != null) {
            // The GlEffect reads this value on Media3's GL thread. The effect parameters are
            // volatile so a paused frame refresh observes slider changes immediately.
            effectGraph.setParams(evaluatedParams)
        }
        
        if (currentUri == uriString) {
            val isPlaying = exoPlayer.isPlaying
            val positionDeltaMs = kotlin.math.abs(exoPlayer.currentPosition - sourcePositionMs)
            // Project/grade state can change many times per second while playing. Only seek for
            // a real playhead jump then; routine slider updates just replace shader uniforms.
            val seekThresholdMs = if (isPlaying) 300L else 80L
            val positionNeedsSeek = forceSeek || positionDeltaMs > seekThresholdMs
            val needsPausedGradeRefresh = !isPlaying && evaluatedParams != null
            if (positionNeedsSeek) {
                cancelPausedGradeRefresh()
                exoPlayer.seekTo(sourcePositionMs)
                lastPausedGradeRefreshMs = android.os.SystemClock.elapsedRealtime()
            } else if (needsPausedGradeRefresh) {
                // Coalesce high-frequency slider updates to at most ~30 refresh seeks/sec. The
                // shader parameters are updated immediately; each rendered frame uses the latest.
                requestPausedGradeRefresh(sourcePositionMs)
            }
            val isReady = exoPlayer.playbackState == Player.STATE_READY
            _state.update { it.copy(usingProxy = useProxy, isReady = isReady) }
            if (isReady) {
                _currentState.value = if (exoPlayer.isPlaying) PreviewState.PLAYING else PreviewState.READY
            }
            return
        }
        cancelPausedGradeRefresh()
        lastPausedGradeRefreshMs = 0L
        _currentState.value = PreviewState.LOADING
        currentUri = uriString
        _firstFrameRendered.value = false
        _state.update { it.copy(error = null, usingProxy = useProxy, isReady = false) }
        
        val mediaItem = MediaItem.fromUri(Uri.parse(uriString))
        exoPlayer.setMediaItem(mediaItem, sourcePositionMs)
        exoPlayer.prepare()
    }

    fun clearMedia() {
        if (currentUri == null) return
        cancelPausedGradeRefresh()
        currentUri = null
        exoPlayer.clearMediaItems()
        _state.update { it.copy(error = null, usingProxy = false, isReady = false) }
        _currentState.value = PreviewState.IDLE
    }

    override fun play() {
        cancelPausedGradeRefresh()
        if (currentUri != null) {
            exoPlayer.play()
        }
    }

    override fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
    }
    
    override fun seekTo(timeUs: Long) {
        cancelPausedGradeRefresh()
        exoPlayer.seekTo(timeUs / 1000)
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    override fun setResolutionFallback(resolutionMode: String) {
        // Fallback or specific player property
    }

    override fun release() {
        cancelPausedGradeRefresh()
        stopTimeTracker()
        effectGraph.release()
        exoPlayer.release()
    }
    
    override val currentPositionUs: Long get() = exoPlayer.currentPosition * 1000
    override val durationUs: Long get() = exoPlayer.duration * 1000
    override val isPlaying: Boolean get() = exoPlayer.isPlaying
}
