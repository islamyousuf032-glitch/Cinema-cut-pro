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
    private var loadGeneration = 0
    private var pauseAfterFirstFrame = false
    private var pauseAfterFrameRunnable: Runnable? = null

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
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        _currentState.value = PreviewState.PLAYING
                    }
                    MediaPlayer.Event.Paused -> {
                        _currentState.value = PreviewState.READY
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        onTimeChanged?.invoke(event.timeChanged * 1000L)
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        cancelPauseAfterFirstFrame()
                        _currentState.value = PreviewState.ERROR
                        _currentError.value = "FFmpeg/LibVLC playback error"
                    }
                    MediaPlayer.Event.Vout -> {
                        if (event.voutCount > 0) {
                            _firstFrameRendered.value = true
                            schedulePauseAfterFirstFrame()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _currentState.value = PreviewState.ERROR
            _currentError.value = "Failed to initialize LibVLC: ${e.message}"
        }
    }

    private fun schedulePauseAfterFirstFrame() {
        if (!pauseAfterFirstFrame) return
        pauseAfterFrameRunnable?.let(mainHandler::removeCallbacks)
        val scheduledGeneration = loadGeneration
        val runnable = Runnable {
            if (scheduledGeneration == loadGeneration && pauseAfterFirstFrame) {
                pauseAfterFirstFrame = false
                if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
                _currentState.value = PreviewState.READY
            }
            pauseAfterFrameRunnable = null
        }
        pauseAfterFrameRunnable = runnable
        // Vout reports a configured video output, not necessarily a presented frame. Give the
        // decoder a short window to submit one before holding the frame for paused grading/UI.
        mainHandler.postDelayed(runnable, 180L)
    }

    private fun cancelPauseAfterFirstFrame() {
        pauseAfterFrameRunnable?.let(mainHandler::removeCallbacks)
        pauseAfterFrameRunnable = null
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
                player.time = sourcePositionMs
            }
            if (player.isPlaying) {
                _currentState.value = PreviewState.PLAYING
            } else if (_firstFrameRendered.value) {
                _currentState.value = PreviewState.READY
            }
            return
        }

        val continuePlayback = player.isPlaying
        cancelPauseAfterFirstFrame()
        loadGeneration += 1
        pauseAfterFirstFrame = !continuePlayback
        _currentState.value = PreviewState.LOADING
        _firstFrameRendered.value = false
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
        cancelPauseAfterFirstFrame()
        mediaPlayer?.play()
    }

    override fun pause() {
        cancelPauseAfterFirstFrame()
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
        get() = mediaPlayer?.isPlaying == true
}
