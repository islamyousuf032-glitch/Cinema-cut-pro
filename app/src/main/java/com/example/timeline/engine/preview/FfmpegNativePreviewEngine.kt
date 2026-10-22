package com.example.timeline.engine.preview

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.BuildConfig
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.media.NativeFfmpegDecoder
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** FFmpeg decodes video; the existing Media3 stack is retained for source-audio playback. */
class FfmpegNativePreviewEngine(context: Context) : PreviewEngine {
    override val engineType = PreviewEngineType.FFMPEG_NATIVE

    // Codec/device coverage is probed by the decoder at open time. These are conservative
    // capabilities: do not advertise HDR/10-bit support as universal across Android devices.
    override val capabilities = PreviewEngineCapabilities(
        canPlayMovingVideo = true,
        canScrub = true,
        canRenderFirstFrame = true,
        canApplyRealtimeColorGrade = false,
        canApplyBasicAdjustments = false,
        canUseProxy = false,
        supports10Bit = false,
        supportsHDR = false,
        supports4K = false,
        currentFailureReason = null
    )

    private val appContext = context.applicationContext
    private val _currentState = MutableStateFlow(PreviewState.IDLE)
    override val currentState = _currentState.asStateFlow()
    private val _currentError = MutableStateFlow<String?>(null)
    override val currentError = _currentError.asStateFlow()
    private val _currentPlaybackFailure = MutableStateFlow<PreviewPlaybackFailure?>(null)
    override val currentPlaybackFailure = _currentPlaybackFailure.asStateFlow()
    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered = _firstFrameRendered.asStateFlow()

    private val trackSelector = DefaultTrackSelector(appContext).apply {
        parameters = buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
    }
    private val audioPlayer = ExoPlayer.Builder(appContext)
        .setTrackSelector(trackSelector)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nativeMutex = Mutex()
    private val loadGeneration = AtomicLong(0L)
    @Volatile private var nativeHandle = 0L
    @Volatile private var released = false
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var requestedUri: String? = null
    private var currentMimeType: String? = null
    private var outputSurface: Surface? = null
    private var ownedSurfaceTexture: SurfaceTexture? = null
    private var attachedTextureView: TextureView? = null
    private var requestedPositionUs = 0L
    private var currentPositionUsValue = 0L
    private var durationUsValue = 0L
    private var playbackSpeed = 1f
    private var playbackRequested = false
    private var latestUseProxy = false
    private var latestEvaluatedParams: VideoAdjustmentParams? = null

    override var onTimeChanged: ((Long) -> Unit)? = null

    override val currentPositionUs: Long
        get() = currentPositionUsValue
    override val durationUs: Long
        get() = durationUsValue
    override val isPlaying: Boolean
        get() = _currentState.value == PreviewState.PLAYING

    init {
        audioPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
    }

    /** Attaches a TextureView output so the decoded frame participates in Compose transforms. */
    fun attachToTextureView(view: TextureView) {
        if (attachedTextureView === view) {
            if (view.isAvailable && outputSurface == null) {
                view.surfaceTexture?.let { onTextureAvailable(view, it) }
            }
            return
        }
        attachedTextureView?.let(::detachFromTextureView)
        attachedTextureView = view
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                onTextureAvailable(view, surfaceTexture)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                if (ownedSurfaceTexture === surfaceTexture) {
                    replaceOutputSurface(newSurface = null, newTexture = null, textureToRelease = surfaceTexture)
                }
                // Keep the texture alive until the native window and MediaCodec surface are detached.
                return false
            }
        }
        if (view.isAvailable) view.surfaceTexture?.let { onTextureAvailable(view, it) }
    }

    private fun onTextureAvailable(view: TextureView, surfaceTexture: SurfaceTexture) {
        if (released || attachedTextureView !== view || ownedSurfaceTexture === surfaceTexture) return
        replaceOutputSurface(Surface(surfaceTexture), surfaceTexture, null)
    }

    fun detachFromTextureView(view: TextureView) {
        if (attachedTextureView !== view) return
        view.surfaceTextureListener = null
        attachedTextureView = null
        val texture = ownedSurfaceTexture
        replaceOutputSurface(newSurface = null, newTexture = null, textureToRelease = texture)
    }

    override fun setSurface(surface: Surface?) {
        if (surface === outputSurface) return
        replaceOutputSurface(newSurface = surface, newTexture = null, textureToRelease = ownedSurfaceTexture)
    }

    private fun replaceOutputSurface(
        newSurface: Surface?,
        newTexture: SurfaceTexture?,
        textureToRelease: SurfaceTexture?
    ) {
        if (released) {
            newSurface?.release()
            textureToRelease?.release()
            return
        }
        val previousSurface = outputSurface
        val previousTexture = ownedSurfaceTexture
        outputSurface = newSurface
        ownedSurfaceTexture = newTexture
        val textureToFree = textureToRelease ?: previousTexture?.takeIf { it !== newTexture }
        if (previousSurface === newSurface && textureToFree == null) return

        val uri = requestedUri
        val positionUs = currentPositionUsValue.coerceAtLeast(0L)
        val shouldResume = playbackRequested || isPlaying
        if (uri == null) {
            scheduleNativeClose(previousSurface, textureToFree)
            return
        }
        beginLoad(uri, positionUs, shouldResume, previousSurfaceToRelease = previousSurface, textureToRelease = textureToFree)
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
        if (released) return
        val targetPositionUs = sourcePositionMs.coerceAtLeast(0L) * 1_000L
        val normalizedMimeType = mimeType?.substringBefore(';')?.trim()?.takeIf(String::isNotBlank)
        val sameSource = requestedUri == uriString && currentMimeType == normalizedMimeType
        requestedUri = uriString
        currentMimeType = normalizedMimeType
        latestUseProxy = useProxy
        latestEvaluatedParams = evaluatedParams

        val handle = nativeHandle
        if (sameSource && handle != 0L) {
            val deltaUs = kotlin.math.abs(currentPositionUsValue - targetPositionUs)
            val seekThresholdUs = if (NativeFfmpegDecoder.isPlaying(handle)) 300_000L else 80_000L
            if (forceSeek || deltaUs > seekThresholdUs) seekTo(targetPositionUs)
            return
        }

        requestedPositionUs = targetPositionUs
        currentPositionUsValue = targetPositionUs
        if (outputSurface == null) {
            _currentState.value = PreviewState.LOADING
            _firstFrameRendered.value = false
            return
        }
        val shouldResume = playbackRequested || isPlaying
        beginLoad(uriString, targetPositionUs, shouldResume)
    }

    private fun beginLoad(
        uri: String,
        positionUs: Long,
        resumePlayback: Boolean,
        previousSurfaceToRelease: Surface? = null,
        textureToRelease: SurfaceTexture? = null
    ) {
        val generation = loadGeneration.incrementAndGet()
        requestedUri = uri
        requestedPositionUs = positionUs.coerceAtLeast(0L)
        playbackRequested = resumePlayback
        val handleToClose = nativeHandle
        nativeHandle = 0L
        _currentState.value = PreviewState.LOADING
        _currentError.value = null
        _currentPlaybackFailure.value = null
        _firstFrameRendered.value = false
        progressJob?.cancel()
        audioPlayer.pause()

        loadJob = scope.launch {
            try {
                withContext(NonCancellable) {
                    nativeMutex.withLock {
                        withContext(Dispatchers.IO) {
                            NativeFfmpegDecoder.close(handleToClose)
                            previousSurfaceToRelease?.release()
                            textureToRelease?.release()
                        }

                        if (released || generation != loadGeneration.get()) return@withLock
                        val surface = outputSurface ?: run {
                            _currentState.value = PreviewState.LOADING
                            return@withLock
                        }
                        val newHandle = withContext(Dispatchers.IO) {
                            NativeFfmpegDecoder.open(appContext, Uri.parse(uri), surface)
                        }
                        if (released || generation != loadGeneration.get()) {
                            withContext(Dispatchers.IO) { NativeFfmpegDecoder.close(newHandle) }
                            return@withLock
                        }

                        nativeHandle = newHandle
                        durationUsValue = NativeFfmpegDecoder.durationUs(newHandle).coerceAtLeast(0L)
                        currentPositionUsValue = positionUs.coerceIn(0L, durationUsValue.takeIf { it > 0L } ?: Long.MAX_VALUE)
                        NativeFfmpegDecoder.setPlaybackSpeed(newHandle, playbackSpeed)
                        NativeFfmpegDecoder.seek(newHandle, currentPositionUsValue)
                        configureAudio(uri, currentPositionUsValue, resumePlayback)
                        if (resumePlayback) {
                            NativeFfmpegDecoder.play(newHandle)
                            audioPlayer.play()
                            _currentState.value = PreviewState.PLAYING
                        } else {
                            _currentState.value = PreviewState.LOADING
                        }
                        startProgressTracker(generation)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (released || generation != loadGeneration.get()) return@launch
                reportFailure(uri, error.message ?: "FFmpeg could not open this video", error)
            }
        }
    }

    private fun configureAudio(uri: String, positionUs: Long, resumePlayback: Boolean) {
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(uri)).also { builder ->
            currentMimeType?.let(builder::setMimeType)
        }.build()
        audioPlayer.setMediaItem(mediaItem, positionUs / 1_000L)
        audioPlayer.prepare()
        audioPlayer.playWhenReady = resumePlayback
    }

    private fun startProgressTracker(generation: Long) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (!released && generation == loadGeneration.get()) {
                val handle = nativeHandle
                if (handle != 0L) {
                    val frameAvailable = NativeFfmpegDecoder.hasFirstFrame(handle)
                    val error = NativeFfmpegDecoder.lastError(handle)
                    if (frameAvailable && !_firstFrameRendered.value) {
                        _firstFrameRendered.value = true
                        _currentState.value = if (NativeFfmpegDecoder.isPlaying(handle)) {
                            PreviewState.PLAYING
                        } else {
                            PreviewState.READY
                        }
                    }
                    if (error.isNotBlank()) {
                        reportFailure(requestedUri.orEmpty(), error, null)
                        return@launch
                    }

                    val position = NativeFfmpegDecoder.positionUs(handle).coerceAtLeast(0L)
                    currentPositionUsValue = position
                    if (_firstFrameRendered.value) onTimeChanged?.invoke(position)
                    if (!NativeFfmpegDecoder.isPlaying(handle) && playbackRequested) {
                        if (durationUsValue > 0L && position >= durationUsValue - 50_000L) {
                            playbackRequested = false
                            audioPlayer.pause()
                            currentPositionUsValue = durationUsValue
                            onTimeChanged?.invoke(durationUsValue)
                            _currentState.value = PreviewState.READY
                            return@launch
                        }
                        if (_firstFrameRendered.value) _currentState.value = PreviewState.READY
                    }
                }
                delay(33L)
            }
        }
    }

    private fun reportFailure(uri: String, message: String, cause: Throwable?) {
        audioPlayer.pause()
        playbackRequested = false
        val parsedUri = runCatching { Uri.parse(uri) }.getOrNull()
        val safeMessage = message.take(512)
        val failure = PreviewPlaybackFailure(
            engineType = engineType,
            uriScheme = parsedUri?.scheme,
            uriAuthority = parsedUri?.authority,
            mimeType = currentMimeType,
            errorCode = null,
            message = safeMessage,
            causeType = cause?.javaClass?.simpleName,
            causeMessage = cause?.message?.take(512),
            category = when {
                cause is SecurityException || safeMessage.contains("source", ignoreCase = true) ->
                    PreviewPlaybackFailureCategory.SOURCE_ACCESS
                safeMessage.contains("support", ignoreCase = true) || safeMessage.contains("decoder", ignoreCase = true) ->
                    PreviewPlaybackFailureCategory.UNSUPPORTED_MEDIA
                else -> PreviewPlaybackFailureCategory.DECODER
            }
        )
        _currentPlaybackFailure.value = failure
        _currentError.value = failure.toUserMessage()
        _currentState.value = PreviewState.ERROR
        if (BuildConfig.DEBUG) {
            android.util.Log.w("FfmpegPreview", failure.toUserMessage())
        }
    }

    private fun scheduleNativeClose(surface: Surface?, texture: SurfaceTexture?) {
        val generation = loadGeneration.incrementAndGet()
        val handle = nativeHandle
        nativeHandle = 0L
        progressJob?.cancel()
        audioPlayer.pause()
        _currentState.value = PreviewState.IDLE
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            nativeMutex.withLock {
                NativeFfmpegDecoder.close(handle)
                surface?.release()
                texture?.release()
            }
        }
    }

    override fun play() {
        if (released) return
        playbackRequested = true
        val handle = nativeHandle
        if (handle == 0L) {
            requestedUri?.let { uri -> beginLoad(uri, requestedPositionUs, resumePlayback = true) }
            return
        }
        if (durationUsValue > 0L && currentPositionUsValue >= durationUsValue - 50_000L) {
            NativeFfmpegDecoder.seek(handle, 0L)
            audioPlayer.seekTo(0L)
            currentPositionUsValue = 0L
        }
        NativeFfmpegDecoder.setPlaybackSpeed(handle, playbackSpeed)
        NativeFfmpegDecoder.play(handle)
        audioPlayer.play()
        _currentState.value = PreviewState.PLAYING
        startProgressTracker(loadGeneration.get())
    }

    override fun pause() {
        playbackRequested = false
        val handle = nativeHandle
        if (handle != 0L) NativeFfmpegDecoder.pause(handle)
        audioPlayer.pause()
        if (_firstFrameRendered.value && _currentState.value != PreviewState.ERROR) {
            _currentState.value = PreviewState.READY
        }
    }

    override fun seekTo(timeUs: Long) {
        val targetUs = timeUs.coerceAtLeast(0L)
        requestedPositionUs = targetUs
        currentPositionUsValue = targetUs
        val handle = nativeHandle
        if (handle == 0L) return
        NativeFfmpegDecoder.seek(handle, targetUs)
        audioPlayer.seekTo(targetUs / 1_000L)
        _currentState.value = if (_firstFrameRendered.value) PreviewState.READY else PreviewState.LOADING
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = if (speed.isFinite()) speed.coerceIn(0.1f, 4f) else 1f
        val handle = nativeHandle
        if (handle != 0L) NativeFfmpegDecoder.setPlaybackSpeed(handle, playbackSpeed)
        audioPlayer.setPlaybackSpeed(playbackSpeed)
    }

    override fun setVolume(volume: Float) {
        audioPlayer.volume = volume.coerceIn(0f, 1f)
    }

    override fun setResolutionFallback(resolutionMode: String) = Unit

    override fun release() {
        if (released) return
        released = true
        playbackRequested = false
        loadGeneration.incrementAndGet()
        progressJob?.cancel()
        onTimeChanged = null
        audioPlayer.pause()
        audioPlayer.release()
        val handle = nativeHandle
        nativeHandle = 0L
        val surface = outputSurface
        outputSurface = null
        val texture = ownedSurfaceTexture
        ownedSurfaceTexture = null
        attachedTextureView?.surfaceTextureListener = null
        attachedTextureView = null
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            nativeMutex.withLock {
                NativeFfmpegDecoder.close(handle)
                surface?.release()
                texture?.release()
            }
        }
        scope.cancel()
        _currentState.value = PreviewState.IDLE
        _firstFrameRendered.value = false
    }
}
