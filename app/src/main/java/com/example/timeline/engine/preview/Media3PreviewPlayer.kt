package com.example.timeline.engine.preview

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.engine.VideoEffectGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
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

    private val _currentPlaybackFailure = MutableStateFlow<PreviewPlaybackFailure?>(null)
    override val currentPlaybackFailure: StateFlow<PreviewPlaybackFailure?> = _currentPlaybackFailure.asStateFlow()

    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()

    private val appContext = context.applicationContext
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setHandleAudioBecomingNoisy(true)
        .build()
    private val effectGraph = VideoEffectGraph(appContext)
    private val _playbackMetrics = MutableStateFlow(PreviewPlaybackMetrics())
    val playbackMetrics: StateFlow<PreviewPlaybackMetrics> = _playbackMetrics.asStateFlow()

    // For legacy compat in codebase
    private val _state = MutableStateFlow(PreviewPlayerState())
    val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

    private var currentUri: String? = null
    private var currentMimeType: String? = null
    private val released = AtomicBoolean(false)

    override var onTimeChanged: ((Long) -> Unit)? = null

    private var timeTrackerJob: kotlinx.coroutines.Job? = null
    private var pausedGradeRefreshJob: kotlinx.coroutines.Job? = null
    private var pausedGradeRefreshPositionMs = 0L
    private var lastPausedGradeRefreshMs = 0L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus= */ true
        )
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentPlaybackFailure.value = null
                _currentError.value = null
            }

            override fun onRenderedFirstFrame() {
                if (com.example.BuildConfig.DEBUG) android.util.Log.d("PLAYER_MEDIA3", "onRenderedFirstFrame called")
                _firstFrameRendered.value = true
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val failure = exoPlayer.playerError?.let(::toPlaybackFailure)
                if (failure != null) {
                    _currentPlaybackFailure.value = failure
                    _currentError.value = failure.toUserMessage()
                }
                _state.update { it.copy(
                    isReady = playbackState == Player.STATE_READY,
                    error = failure?.toUserMessage() ?: it.error
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

            override fun onPlayerError(error: PlaybackException) {
                val failure = toPlaybackFailure(error)
                _currentPlaybackFailure.value = failure
                _currentError.value = failure.toUserMessage()
                _state.update { it.copy(error = failure.toUserMessage(), isReady = false, isPlaying = false) }
                _currentState.value = PreviewState.ERROR
                stopTimeTracker()
            }
        })
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                _playbackMetrics.update {
                    it.copy(
                        videoDecoderName = decoderName,
                        decoderInitializationDurationMs = initializationDurationMs
                    )
                }
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                _playbackMetrics.update {
                    it.copy(
                        droppedVideoFrames = it.droppedVideoFrames + droppedFrames.coerceAtLeast(0),
                        droppedFrameReportWindowMs = elapsedMs.coerceAtLeast(0L)
                    )
                }
            }

            override fun onVideoFrameProcessingOffset(
                eventTime: AnalyticsListener.EventTime,
                totalProcessingOffsetUs: Long,
                frameCount: Int
            ) {
                if (frameCount <= 0) return
                val averageOffsetMs = totalProcessingOffsetUs.toDouble()
                    .div(frameCount.toDouble())
                    .div(1_000.0)
                    .toFloat()
                _playbackMetrics.update {
                    it.copy(
                        averageVideoFrameProcessingOffsetMs = averageOffsetMs,
                        processedVideoFrameSamples = it.processedVideoFrameSamples + frameCount,
                        lateFrameProcessingWindows = it.lateFrameProcessingWindows +
                            if (averageOffsetMs < 0f) 1L else 0L
                    )
                }
            }
        })

        try {
            exoPlayer.setVideoEffects(effectGraph.getEffects())
            if (com.example.BuildConfig.DEBUG) android.util.Log.d("PLAYER_MEDIA3", "Enabled shared GLSL color effect for preview")
        } catch (e: Exception) {
            val failure = PreviewPlaybackFailure(
                engineType = engineType,
                uriScheme = null,
                uriAuthority = null,
                mimeType = null,
                errorCode = null,
                message = "GPU color grading could not initialize",
                causeType = e.javaClass.simpleName,
                causeMessage = e.message
            )
            _currentPlaybackFailure.value = failure
            _currentError.value = failure.toUserMessage()
            _currentState.value = PreviewState.ERROR
            if (com.example.BuildConfig.DEBUG) android.util.Log.e("Media3PreviewPlayer", "Failed to initialize the video effect graph", e)
        }
    }

    private fun sanitizeSourceDetail(detail: String?, sourceUri: Uri?): String? {
        if (detail == null) return null
        var sanitized = detail
        if (sourceUri != null) {
            val safeContext = when {
                sourceUri.scheme.isNullOrBlank() -> "source"
                sourceUri.authority.isNullOrBlank() -> "${sourceUri.scheme} URI"
                else -> "${sourceUri.scheme}://${sourceUri.authority}"
            }
            sourceUri.toString().takeIf(String::isNotBlank)?.let { rawUri ->
                sanitized = sanitized.replace(rawUri, safeContext)
            }
            sourceUri.path?.takeIf(String::isNotBlank)?.let { path ->
                sanitized = sanitized.replace(path, "[source path]")
            }
        }
        return sanitized.take(512)
    }

    private fun toPlaybackFailure(error: PlaybackException): PreviewPlaybackFailure {
        val sourceUri = currentUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val category = when (error.errorCode) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                PreviewPlaybackFailureCategory.UNSUPPORTED_MEDIA
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> PreviewPlaybackFailureCategory.DRM
            in 2000..2999 -> PreviewPlaybackFailureCategory.SOURCE_ACCESS
            in 4000..4999 -> PreviewPlaybackFailureCategory.DECODER
            in 5000..5999 -> PreviewPlaybackFailureCategory.AUDIO
            else -> PreviewPlaybackFailureCategory.PLAYBACK
        }
        return PreviewPlaybackFailure(
            engineType = engineType,
            uriScheme = sourceUri?.scheme,
            uriAuthority = sourceUri?.authority,
            mimeType = currentMimeType,
            errorCode = error.errorCode,
            message = sanitizeSourceDetail(error.message, sourceUri) ?: error.javaClass.simpleName,
            causeType = error.cause?.javaClass?.simpleName,
            causeMessage = sanitizeSourceDetail(error.cause?.message, sourceUri),
            category = category,
            errorCodeName = error.errorCodeName
        )
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
        if (!released.get()) exoPlayer.setVideoSurface(surface)
    }

    override fun loadMedia(
        uriString: String,
        useProxy: Boolean,
        sourcePositionMs: Long,
        evaluatedParams: VideoAdjustmentParams?,
        presentationTimeUs: Long,
        forceSeek: Boolean,
        mimeType: String?
    ) {
        if (released.get()) return
        val safePositionMs = sourcePositionMs.coerceAtLeast(0L)
        val normalizedMimeType = mimeType?.substringBefore(';')?.trim()?.takeIf(String::isNotBlank)
        if (com.example.BuildConfig.DEBUG) {
            android.util.Log.d("PLAYER_MEDIA3", "Loading ${normalizedMimeType ?: "unknown media"} at $safePositionMs ms; proxy=$useProxy")
        }
        
        if (evaluatedParams != null) {
            // The GlEffect reads this value on Media3's GL thread. The effect parameters are
            // volatile so a paused frame refresh observes slider changes immediately.
            effectGraph.setParams(evaluatedParams)
        }
        
        val sameSource = currentUri == uriString && currentMimeType == normalizedMimeType
        if (sameSource && exoPlayer.playerError == null) {
            val isPlaying = exoPlayer.isPlaying
            val positionDeltaMs = kotlin.math.abs(exoPlayer.currentPosition - safePositionMs)
            // Project/grade state can change many times per second while playing. Only seek for
            // a real playhead jump then; routine slider updates just replace shader uniforms.
            val seekThresholdMs = if (isPlaying) 300L else 80L
            val positionNeedsSeek = forceSeek || positionDeltaMs > seekThresholdMs
            val needsPausedGradeRefresh = !isPlaying && evaluatedParams != null
            if (positionNeedsSeek) {
                cancelPausedGradeRefresh()
                exoPlayer.seekTo(safePositionMs)
                lastPausedGradeRefreshMs = android.os.SystemClock.elapsedRealtime()
            } else if (needsPausedGradeRefresh) {
                // Coalesce high-frequency slider updates to at most ~30 refresh seeks/sec. The
                // shader parameters are updated immediately; each rendered frame uses the latest.
                requestPausedGradeRefresh(safePositionMs)
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
        _playbackMetrics.value = PreviewPlaybackMetrics()
        _currentState.value = PreviewState.LOADING
        currentUri = uriString
        currentMimeType = normalizedMimeType
        _currentError.value = null
        _currentPlaybackFailure.value = null
        _firstFrameRendered.value = false
        _state.update { it.copy(error = null, usingProxy = useProxy, isReady = false) }

        try {
            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(uriString))
            normalizedMimeType?.let(mediaItemBuilder::setMimeType)
            exoPlayer.setMediaItem(mediaItemBuilder.build(), safePositionMs)
            exoPlayer.prepare()
        } catch (error: Exception) {
            val sourceUri = runCatching { Uri.parse(uriString) }.getOrNull()
            val failure = PreviewPlaybackFailure(
                engineType = engineType,
                uriScheme = sourceUri?.scheme,
                uriAuthority = sourceUri?.authority,
                mimeType = normalizedMimeType,
                errorCode = null,
                message = "Media source could not be opened",
                causeType = error.javaClass.simpleName,
                causeMessage = sanitizeSourceDetail(error.message, sourceUri),
                category = if (sourceUri?.scheme == "content" || sourceUri?.scheme == "file") {
                    PreviewPlaybackFailureCategory.SOURCE_ACCESS
                } else {
                    PreviewPlaybackFailureCategory.PLAYBACK
                }
            )
            _currentPlaybackFailure.value = failure
            _currentError.value = failure.toUserMessage()
            _state.update { it.copy(error = failure.toUserMessage(), isReady = false, isPlaying = false) }
            _currentState.value = PreviewState.ERROR
            stopTimeTracker()
        }
    }

    fun clearMedia() {
        if (released.get()) return
        cancelPausedGradeRefresh()
        currentUri = null
        currentMimeType = null
        _currentError.value = null
        _currentPlaybackFailure.value = null
        _firstFrameRendered.value = false
        exoPlayer.clearMediaItems()
        _state.update { it.copy(error = null, usingProxy = false, isReady = false, isPlaying = false) }
        _currentState.value = PreviewState.IDLE
    }

    override fun play() {
        if (released.get()) return
        cancelPausedGradeRefresh()
        if (currentUri != null) exoPlayer.play()
    }

    override fun pause() {
        if (!released.get()) exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
    }
    
    override fun seekTo(timeUs: Long) {
        if (released.get()) return
        cancelPausedGradeRefresh()
        exoPlayer.seekTo((timeUs.coerceAtLeast(0L)) / 1000L)
    }

    override fun setPlaybackSpeed(speed: Float) {
        require(speed.isFinite() && speed > 0f) {
            "Playback speed must be finite and greater than zero."
        }
        if (!released.get()) exoPlayer.setPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        if (released.get()) return
        exoPlayer.volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 1f
    }

    override fun setResolutionFallback(resolutionMode: String) {
        // Resolution selection is handled by the timeline's proxy/source policy.
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        cancelPausedGradeRefresh()
        stopTimeTracker()
        scope.cancel()
        exoPlayer.release()
        effectGraph.release()
        _currentState.value = PreviewState.IDLE
    }

    override val currentPositionUs: Long
        get() = if (released.get()) 0L else exoPlayer.currentPosition.coerceAtLeast(0L) * 1000L
    override val durationUs: Long
        get() = if (released.get()) 0L else exoPlayer.duration.takeIf { it >= 0L }?.times(1000L) ?: 0L
    override val isPlaying: Boolean get() = !released.get() && exoPlayer.isPlaying
}
