package com.example.timeline.engine.preview.native

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
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
    private var pendingMediaStart = false
    private var attachedLayout: org.videolan.libvlc.util.VLCVideoLayout? = null
    private var layoutAttachListener: View.OnAttachStateChangeListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var loadGeneration = 0
    @Volatile private var pauseAfterFirstFrame = false
    @Volatile private var videoOutputReady = false
    @Volatile private var initialPositionMs = 0L
    @Volatile private var lastWarmupTimeMs: Long? = null
    @Volatile private var requestedVolumePercent: Int = 100
    private var pauseAfterFrameRunnable: Runnable? = null
    private var firstFrameWatchdogRunnable: Runnable? = null

    override var onTimeChanged: ((Long) -> Unit)? = null

    init {
        try {
            val options = arrayListOf(
                // Match VLC for Android 3.5.1's configured video-output module name.
                "--vout=android_display,none",
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
                _currentError.value = "LibVLC playback error"
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
                    player.setVolume(requestedVolumePercent)
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
                    player.setVolume(requestedVolumePercent)
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

        runCatching {
            if (mediaPlayer?.vlcVout?.areViewsAttached() == true) mediaPlayer?.vlcVout?.detachViews()
        }
        this.surface = surface
        if (surface == null || !surface.isValid) return

        try {
            mediaPlayer?.vlcVout?.setVideoSurface(surface, null)
            mediaPlayer?.vlcVout?.attachViews()
            startPendingMediaIfOutputReady()
        } catch (error: Exception) {
            _currentError.value = "LibVLC could not attach the video surface: ${error.message ?: error.javaClass.simpleName}"
            _currentState.value = PreviewState.ERROR
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("NativeVlcPreviewEngine", "Failed to attach surface", error)
            }
        }
    }

    private fun startPendingMediaIfOutputReady() {
        val player = mediaPlayer ?: return
        if (!pendingMediaStart || !player.vlcVout.areViewsAttached()) return

        pendingMediaStart = false
        if (pauseAfterFirstFrame) {
            // Decode one frame silently for a paused timeline preview.
            player.setVolume(0)
            scheduleFirstFrameWatchdog()
        } else {
            player.setVolume(requestedVolumePercent)
        }
        player.play()
    }

    private fun attachLayoutNow(layout: org.videolan.libvlc.util.VLCVideoLayout) {
        if (attachedLayout !== layout || !layout.isAttachedToWindow) return
        val player = mediaPlayer ?: return
        try {
            if (!player.vlcVout.areViewsAttached()) {
                // Match VideoLAN's player setup: subtitles enabled and the layout's default
                // SurfaceView retained for the platform video-output path.
                player.attachViews(layout, null, true, false)
            }
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "NativeVlcPreviewEngine",
                    "VLC output attached=${player.vlcVout.areViewsAttached()} layoutAttached=${layout.isAttachedToWindow}"
                )
            }
            startPendingMediaIfOutputReady()
        } catch (error: Exception) {
            _currentError.value = "LibVLC could not attach its video output: ${error.message ?: error.javaClass.simpleName}"
            _currentState.value = PreviewState.ERROR
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("NativeVlcPreviewEngine", "Failed to attach VLCVideoLayout", error)
            }
        }
    }

    private fun handleLayoutDetached(layout: org.videolan.libvlc.util.VLCVideoLayout) {
        if (attachedLayout !== layout) return
        val player = mediaPlayer ?: return
        val resumePlayback = !pauseAfterFirstFrame && (player.isPlaying || pendingMediaStart)
        if (player.isPlaying) player.pause()
        runCatching { player.detachViews() }
        videoOutputReady = false

        if (currentUri != null) {
            initialPositionMs = player.time.coerceAtLeast(0L)
            lastWarmupTimeMs = null
            cancelPauseAfterFirstFrame()
            pauseAfterFirstFrame = !resumePlayback
            pendingMediaStart = true
            _firstFrameRendered.value = false
            player.setVolume(if (pauseAfterFirstFrame) 0 else requestedVolumePercent)
            _currentState.value = PreviewState.LOADING
        }
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
            pendingMediaStart = true
            if (pauseAfterFirstFrame) {
                // The silent first-frame warm-up starts only after VLC has a real video output.
                player.setVolume(0)
            } else {
                player.setVolume(requestedVolumePercent)
            }
            // Engine selection loads the source before Compose creates/attaches VLCVideoLayout.
            // Starting now can decode into no output and leave the viewport permanently black.
            startPendingMediaIfOutputReady()
        } catch (error: Exception) {
            pendingMediaStart = false
            cancelPauseAfterFirstFrame()
            _currentState.value = PreviewState.ERROR
            _currentError.value = error.message ?: "Failed to load media"
        }
    }

    override fun play() {
        if (currentUri == null) return
        val wasWarmingUp = pauseAfterFirstFrame
        cancelPauseAfterFirstFrame()
        val player = mediaPlayer ?: return
        player.setVolume(requestedVolumePercent)
        if (wasWarmingUp && player.time != initialPositionMs) {
            player.time = initialPositionMs
        }
        pendingMediaStart = true
        if (player.vlcVout.areViewsAttached()) {
            pendingMediaStart = false
            player.play()
        } else {
            // A manually selected engine can receive play() before Compose has attached its view.
            _currentState.value = PreviewState.LOADING
        }
    }

    override fun pause() {
        // Keep the initial silent warm-up alive so a paused timeline can still show its first frame.
        if (pauseAfterFirstFrame) return
        val player = mediaPlayer ?: return
        if (pendingMediaStart && !player.vlcVout.areViewsAttached()) {
            // A user can press pause before the view attachment completes. Convert the pending
            // start to the same silent one-frame warm-up used for a paused project load.
            pauseAfterFirstFrame = true
            initialPositionMs = player.time.coerceAtLeast(0L)
            lastWarmupTimeMs = null
            _firstFrameRendered.value = false
            player.setVolume(0)
            return
        }
        player.pause()
    }

    override fun seekTo(timeUs: Long) {
        mediaPlayer?.time = timeUs / 1000
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (speed.isFinite() && speed > 0f) mediaPlayer?.rate = speed
    }

    override fun setVolume(volume: Float) {
        if (!volume.isFinite()) return
        requestedVolumePercent = (volume.coerceIn(0f, 1f) * 100f).toInt()
        if (!pauseAfterFirstFrame) mediaPlayer?.setVolume(requestedVolumePercent)
    }

    override fun setResolutionFallback(resolutionMode: String) {
        // Not used by VLC directly
    }

    fun attachToVideoLayout(layout: org.videolan.libvlc.util.VLCVideoLayout) {
        if (attachedLayout === layout) {
            if (layout.isAttachedToWindow) attachLayoutNow(layout)
            return
        }

        detachFromVideoLayout()
        attachedLayout = layout
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                attachLayoutNow(layout)
            }

            override fun onViewDetachedFromWindow(view: View) {
                handleLayoutDetached(layout)
            }
        }
        layoutAttachListener = listener
        layout.addOnAttachStateChangeListener(listener)

        // AndroidView's factory runs before the view is attached to Compose's window. Defer the
        // VLC surface handoff until the attach callback rather than starting decode into no vout.
        if (layout.isAttachedToWindow) attachLayoutNow(layout)
        else layout.post { if (layout.isAttachedToWindow) attachLayoutNow(layout) }
    }

    fun detachFromVideoLayout(layoutBeingReleased: org.videolan.libvlc.util.VLCVideoLayout? = null) {
        val layout = attachedLayout ?: return
        if (layoutBeingReleased != null && layoutBeingReleased !== layout) return
        layoutAttachListener?.let(layout::removeOnAttachStateChangeListener)
        layoutAttachListener = null
        handleLayoutDetached(layout)
        attachedLayout = null
    }

    override fun release() {
        detachFromVideoLayout()
        cancelPauseAfterFirstFrame()
        pendingMediaStart = false
        runCatching { mediaPlayer?.vlcVout?.detachViews() }
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
