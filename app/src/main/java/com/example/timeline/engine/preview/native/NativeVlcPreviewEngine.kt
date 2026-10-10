package com.example.timeline.engine.preview.native

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.engine.preview.PreviewEngine
import com.example.timeline.engine.preview.PreviewEngineType
import com.example.timeline.engine.preview.PreviewEngineCapabilities
import com.example.timeline.engine.preview.PreviewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class NativeVlcPreviewEngine(
    private val context: Context,
    override val engineType: PreviewEngineType = PreviewEngineType.VLC_NATIVE
) : PreviewEngine {
    
    override val capabilities: PreviewEngineCapabilities = PreviewEngineCapabilities(
        canPlayMovingVideo = true,
        canScrub = true,
        canRenderFirstFrame = true,
        canApplyRealtimeColorGrade = false,
        canApplyBasicAdjustments = false,
        canUseProxy = true,
        currentFailureReason = null
    )
    
    private val _currentState = MutableStateFlow(PreviewState.IDLE)
    override val currentState = _currentState.asStateFlow()

    private val _currentError = MutableStateFlow<String?>(null)
    override val currentError = _currentError.asStateFlow()

    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered = _firstFrameRendered.asStateFlow()

    private var surface: Surface? = null
    
    private var libvlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var currentUri: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var loadGeneration = 0
    @Volatile private var pauseAfterFirstFrame = false
    @Volatile private var videoOutputReady = false
    @Volatile private var initialPositionMs = 0L
    @Volatile private var lastWarmupTimeMs: Long? = null
    private var pauseAfterFrameRunnable: Runnable? = null
    private var firstFrameWatchdogRunnable: Runnable? = null

    override var onTimeChanged: ((Long) -> Unit)? = null

    init {
        try {
            val options = arrayListOf(
                "--vout=android-display",
                "--avcodec-hw=any",
                "--network-caching=500",
                "--file-caching=250"
            )
            // Keep VLC's late-frame dropping and frame-skip defaults enabled. Disabling both
            // made the old player preserve every frame by stalling playback on slower phones.
            libvlc = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libvlc)

            mediaPlayer?.setEventListener { event ->
                val dispatch = Runnable { handlePlayerEvent(event) }
                if (Looper.myLooper() == Looper.getMainLooper()) dispatch.run() else mainHandler.post(dispatch)
            }
        } catch (e: Exception) {
            _currentState.value = PreviewState.ERROR
            _currentError.value = "Failed to initialize LibVLC: ${e.message}"
        }
    }

    private fun handlePlayerEvent(event: MediaPlayer.Event) {
        if (mediaPlayer == null) return
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                // Initial paused-preview decoding is an internal warm-up, not user playback.
                // Keep loading until VLC advances after creating the video output.
                _currentState.value = when {
                    !pauseAfterFirstFrame -> PreviewState.PLAYING
                    _firstFrameRendered.value -> PreviewState.READY
                    else -> PreviewState.LOADING
                }
            }
            MediaPlayer.Event.Paused -> {
                _currentState.value = PreviewState.READY
            }
            MediaPlayer.Event.TimeChanged -> {
                if (pauseAfterFirstFrame) {
                    // Vout only confirms that VLC created an output. Wait for playback time
                    // to advance as well before treating the warm-up as a decoded preview.
                    val previousWarmupTime = lastWarmupTimeMs
                    lastWarmupTimeMs = event.timeChanged
                    if (videoOutputReady && previousWarmupTime != null && event.timeChanged != previousWarmupTime) {
                        _firstFrameRendered.value = true
                        cancelFirstFrameWatchdog()
                        if (pauseAfterFrameRunnable == null) schedulePauseAfterFirstFrame()
                    }
                } else {
                    if (videoOutputReady) _firstFrameRendered.value = true
                    onTimeChanged?.invoke(event.timeChanged * 1000L)
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                cancelPauseAfterFirstFrame()
                _currentState.value = PreviewState.ERROR
                _currentError.value = "FFmpeg/LibVLC playback error"
            }
            MediaPlayer.Event.Vout -> {
                if (event.voutCount > 0) videoOutputReady = true
            }
        }
    }

    private fun schedulePauseAfterFirstFrame() {
        if (!pauseAfterFirstFrame) return
        pauseAfterFrameRunnable?.let(mainHandler::removeCallbacks)
        val scheduledGeneration = loadGeneration
        val runnable = Runnable {
            if (scheduledGeneration == loadGeneration && pauseAfterFirstFrame) {
                pauseAfterFirstFrame = false
                cancelFirstFrameWatchdog()
                mediaPlayer?.let { player ->
                    if (player.isPlaying) player.pause()
                    player.setVolume(100)
                    // Warm-up is intentionally silent to the timeline. Restore the exact requested
                    // frame so a paused preview never lands a few decoded frames past the playhead.
                    if (player.time != initialPositionMs) player.time = initialPositionMs
                }
                _currentState.value = PreviewState.READY
            }
            pauseAfterFrameRunnable = null
        }
        pauseAfterFrameRunnable = runnable
        // Vout plus an advancing media clock indicate decode progress, but the frame may still be
        // in flight. Give the decoder a short window to submit it before holding the paused frame.
        mainHandler.postDelayed(runnable, 350L)
    }

    private fun scheduleFirstFrameWatchdog() {
        firstFrameWatchdogRunnable?.let(mainHandler::removeCallbacks)
        val scheduledGeneration = loadGeneration
        val runnable = Runnable {
            if (scheduledGeneration == loadGeneration && pauseAfterFirstFrame && !_firstFrameRendered.value) {
                cancelPauseAfterFirstFrame()
                mediaPlayer?.let { player ->
                    if (player.isPlaying) player.pause()
                    player.setVolume(100)
                }
                _currentError.value = "LibVLC did not produce a video frame within 5 seconds"
                _currentState.value = PreviewState.ERROR
            }
            firstFrameWatchdogRunnable = null
        }
        firstFrameWatchdogRunnable = runnable
        mainHandler.postDelayed(runnable, 5_000L)
    }

    private fun cancelFirstFrameWatchdog() {
        firstFrameWatchdogRunnable?.let(mainHandler::removeCallbacks)
        firstFrameWatchdogRunnable = null
    }

    private fun cancelPauseAfterFirstFrame() {
        pauseAfterFrameRunnable?.let(mainHandler::removeCallbacks)
        pauseAfterFrameRunnable = null
        cancelFirstFrameWatchdog()
        pauseAfterFirstFrame = false
    }

    override fun setSurface(surface: Surface?) {
        if (this.surface === surface) return
        
        try {
            if (mediaPlayer?.vlcVout?.areViewsAttached() == true) {
                mediaPlayer?.vlcVout?.detachViews()
            }
        } catch (e: Exception) {
            // ignore
        }
        
        this.surface = surface
        if (surface != null && surface.isValid) {
            try {
                mediaPlayer?.vlcVout?.setVideoSurface(surface, null)
                mediaPlayer?.vlcVout?.attachViews()
            } catch (e: Exception) {
                android.util.Log.e("NativeVlcPreviewEngine", "Failed to attach surface", e)
            }
        }
    }

    override fun loadMedia(
        uriString: String,
        useProxy: Boolean,
        sourcePositionMs: Long,
        evaluatedParams: VideoAdjustmentParams?,
        presentationTimeUs: Long,
        forceSeek: Boolean
    ) {
        val vlc = libvlc ?: return
        val player = mediaPlayer ?: return
        _currentError.value = null

        if (uriString == currentUri) {
            val positionDeltaMs = kotlin.math.abs(player.time - sourcePositionMs)
            if (sourcePositionMs >= 0L && (forceSeek || positionDeltaMs > 80L)) {
                if (pauseAfterFirstFrame) {
                    initialPositionMs = sourcePositionMs
                    lastWarmupTimeMs = null
                    pauseAfterFrameRunnable?.let(mainHandler::removeCallbacks)
                    pauseAfterFrameRunnable = null
                    _firstFrameRendered.value = false
                }
                player.time = sourcePositionMs
            }
            if (pauseAfterFirstFrame) {
                if (_firstFrameRendered.value) _currentState.value = PreviewState.READY
            } else if (player.isPlaying) {
                _currentState.value = PreviewState.PLAYING
            } else if (_firstFrameRendered.value) {
                _currentState.value = PreviewState.READY
            }
            return
        }

        val continuePlayback = player.isPlaying && !pauseAfterFirstFrame
        cancelPauseAfterFirstFrame()
        loadGeneration += 1
        pauseAfterFirstFrame = !continuePlayback
        _currentState.value = PreviewState.LOADING
        _firstFrameRendered.value = false
        videoOutputReady = false
        initialPositionMs = sourcePositionMs.coerceAtLeast(0L)
        lastWarmupTimeMs = null
        currentUri = uriString

        try {
            val uri = Uri.parse(uriString)
            val media = if (uri.scheme == "file" || uri.scheme == null) {
                Media(vlc, uri.path)
            } else {
                Media(vlc, uri)
            }
            if (sourcePositionMs > 0L) {
                media.addOption(":start-time=${sourcePositionMs / 1000f}")
            }
            player.media = media
            media.release()
            if (pauseAfterFirstFrame) {
                // Decode a preview frame without leaking audio from a paused timeline load.
                player.setVolume(0)
                scheduleFirstFrameWatchdog()
            }
            // Start decoding to produce the first preview frame. A paused load is paused only
            // after VLC has had time to submit that frame; play()+pause() in one call froze black.
            player.play()
        } catch (error: Exception) {
            cancelPauseAfterFirstFrame()
            _currentState.value = PreviewState.ERROR
            _currentError.value = error.message ?: "Failed to load media"
        }
    }

    override fun play() {
        val wasWarmingUp = pauseAfterFirstFrame
        cancelPauseAfterFirstFrame()
        mediaPlayer?.let { player ->
            if (wasWarmingUp) {
                player.setVolume(100)
                if (player.time != initialPositionMs) player.time = initialPositionMs
            }
            player.play()
        }
    }

    override fun pause() {
        // LibVLC must briefly run to decode and submit a frame for a paused preview. The initial
        // Compose playback-state effect calls pause() as soon as the screen is composed; pausing
        // here used to cancel the first-frame handoff and leave the viewport black/stale.
        if (pauseAfterFirstFrame) return
        mediaPlayer?.pause()
    }

    override fun seekTo(timeUs: Long) {
        mediaPlayer?.time = timeUs / 1000
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.rate = speed
    }

    override fun setResolutionFallback(resolutionMode: String) {
        // Not used by VLC directly
    }

    fun attachToVideoLayout(layout: org.videolan.libvlc.util.VLCVideoLayout) {
        try {
            if (mediaPlayer?.vlcVout?.areViewsAttached() == true) {
                mediaPlayer?.vlcVout?.detachViews()
            }
            mediaPlayer?.attachViews(layout, null, true, false)
        } catch (e: Exception) {
            android.util.Log.e("NativeVlcPreviewEngine", "Failed to attach VLCVideoLayout", e)
        }
    }

    fun detachFromVideoLayout() {
        try {
            mediaPlayer?.detachViews()
        } catch (e: Exception) {
            android.util.Log.e("NativeVlcPreviewEngine", "Failed to detach VLCVideoLayout", e)
        }
    }

    override fun release() {
        cancelPauseAfterFirstFrame()
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer?.release()
        libvlc?.release()
        mediaPlayer = null
        libvlc = null
        surface = null
        currentUri = null
        _currentState.value = PreviewState.IDLE
    }

    override val currentPositionUs: Long
        get() = (mediaPlayer?.time ?: 0L) * 1000

    override val durationUs: Long
        get() = (mediaPlayer?.length ?: 0L) * 1000

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true && !pauseAfterFirstFrame
}
