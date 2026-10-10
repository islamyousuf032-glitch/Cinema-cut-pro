package com.example.timeline.engine.preview.native

import android.content.Context
import android.net.Uri
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

class NativeVlcPreviewEngine(private val context: Context) : PreviewEngine {
    override val engineType = PreviewEngineType.VLC_NATIVE
    
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
    
    override var onTimeChanged: ((Long) -> Unit)? = null

    init {
        try {
            val options = ArrayList<String>()
            options.add("--no-drop-late-frames")
            options.add("--no-skip-frames")
            options.add("--vout=android-display")
            options.add("--network-caching=150")
            options.add("--file-caching=150")
            options.add("-vv")
            libvlc = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libvlc)
            
            mediaPlayer?.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        _currentState.value = PreviewState.PLAYING
                        _firstFrameRendered.value = true
                    }
                    MediaPlayer.Event.Paused -> {
                        _currentState.value = PreviewState.READY
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        onTimeChanged?.invoke(event.timeChanged * 1000L) // Convert ms to us
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        _currentState.value = PreviewState.ERROR
                        _currentError.value = "VLC MediaPlayer Error"
                    }
                    MediaPlayer.Event.Vout -> {
                        if (event.voutCount > 0) {
                            _firstFrameRendered.value = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _currentState.value = PreviewState.ERROR
            _currentError.value = "Failed to initialize LibVLC: ${e.message}"
        }
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
        presentationTimeUs: Long
    ) {
        if (libvlc == null || mediaPlayer == null) return
        
        if (uriString != currentUri) {
            _currentState.value = PreviewState.LOADING
            _firstFrameRendered.value = false
            currentUri = uriString
            try {
                val uri = Uri.parse(uriString)
                val media = if (uri.scheme == "file" || uri.scheme == null) {
                    Media(libvlc, uri.path)
                } else {
                    Media(libvlc, uri)
                }
                
                // For faster seek/playback locally
                media.addOption(":no-audio") // If we just want video preview, or allow audio? Let's leave audio enabled by default unless needed
                media.addOption(":start-time=${sourcePositionMs / 1000f}")
                
                mediaPlayer?.media = media
                media.release()
            } catch (e: Exception) {
                _currentState.value = PreviewState.ERROR
                _currentError.value = e.message ?: "Failed to load media"
                return
            }
        } else {
            mediaPlayer?.time = sourcePositionMs
        }

        // Let VLC handle play/pause via explicit play/pause method calls.
        // If we just need to seek, let's just seek.
        // Note: VLC might not update screen when seeking while paused, 
        // but we rely on StillFrameFallback or explicit play to update the screen for now.
        if (_currentState.value != PreviewState.PLAYING) {
            mediaPlayer?.play()
            // We use a small delay or rely on UI to pause it properly if needed, but for now we just pause.
            mediaPlayer?.pause()
        }
    }

    override fun play() {
        mediaPlayer?.play()
    }

    override fun pause() {
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
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer?.release()
        libvlc?.release()
        mediaPlayer = null
        libvlc = null
        surface = null
    }

    override val currentPositionUs: Long
        get() = (mediaPlayer?.time ?: 0L) * 1000

    override val durationUs: Long
        get() = (mediaPlayer?.length ?: 0L) * 1000

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true
}
