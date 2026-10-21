package com.example.timeline.engine.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.view.Surface
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.media.NativeMcrawDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

/**
 * Frame-at-a-time MCRAW preview. The upstream decoder and demosaic run in native code;
 * this preview engine deliberately avoids Media3/MediaMetadataRetriever, neither of which
 * understands the MotionCam container.
 */
class MotionCamRawPreviewEngine(private val context: Context) : PreviewEngine {
    override val engineType = PreviewEngineType.MOTIONCAM_RAW

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

    private val _currentState = MutableStateFlow(PreviewState.IDLE)
    override val currentState = _currentState.asStateFlow()
    private val _currentError = MutableStateFlow<String?>(null)
    override val currentError = _currentError.asStateFlow()
    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered = _firstFrameRendered.asStateFlow()
    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap = _currentBitmap.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val decoderMutex = Mutex()
    private var handle = 0L
    private var openedUri: String? = null
    private var frameTimestampsNs = LongArray(0)
    private var currentFrameIndex = 0
    private var durationUsValue = 0L
    private var currentPositionUsValue = 0L
    private var requestedUri: String? = null
    private var playbackSpeed = 1f
    private var loadJob: Job? = null
    private var playbackJob: Job? = null
    private var playWhenReady = false
    @Volatile private var released = false

    override var onTimeChanged: ((Long) -> Unit)? = null

    override val currentPositionUs: Long
        get() = currentPositionUsValue
    override val durationUs: Long
        get() = durationUsValue
    override val isPlaying: Boolean
        get() = playbackJob?.isActive == true

    override fun setSurface(surface: Surface?) = Unit

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
        val uriChanged = uriString != requestedUri
        requestedUri = uriString
        val resumePlayback = playbackJob?.isActive == true || playWhenReady
        if (resumePlayback) playWhenReady = true
        playbackJob?.cancel()
        playbackJob = null
        loadJob?.cancel()
        _currentError.value = null
        if (uriChanged) {
            _currentState.value = PreviewState.LOADING
            _firstFrameRendered.value = false
            _currentBitmap.value = null
        }

        val targetPositionMs = sourcePositionMs.coerceAtLeast(0L)
        loadJob = scope.launch {
            try {
                val frame = withContext(Dispatchers.IO) {
                    decoderMutex.withLock {
                        ensureOpen(uriString)
                        if (frameTimestampsNs.isEmpty()) throw IllegalStateException("MotionCam recording contains no frames")
                        val targetNs = targetPositionMs.coerceAtMost(Long.MAX_VALUE / 1_000_000L) * 1_000_000L
                        val index = nearestFrameIndex(frameTimestampsNs, targetNs)
                        val bitmap = decodeFrame(index)
                        val timestampUs = frameTimestampsNs[index] / 1_000L
                        currentFrameIndex = index
                        currentPositionUsValue = timestampUs
                        bitmap
                    }
                }
                if (released || requestedUri != uriString) return@launch
                _currentBitmap.value = frame
                _firstFrameRendered.value = true
                _currentError.value = null
                val shouldResume = resumePlayback && playWhenReady
                _currentState.value = if (shouldResume) PreviewState.PLAYING else PreviewState.READY
                onTimeChanged?.invoke(currentPositionUsValue)
                if (shouldResume) startPlaybackLoop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (released || requestedUri != uriString) return@launch
                _currentError.value = error.message ?: "Could not decode MotionCam RAW preview"
                _currentState.value = PreviewState.ERROR
            }
        }
    }

    private suspend fun ensureOpen(uriString: String) {
        if (openedUri == uriString && handle != 0L) return
        if (handle != 0L) NativeMcrawDecoder.close(handle)
        handle = 0L
        openedUri = null
        frameTimestampsNs = LongArray(0)
        val opened = NativeMcrawDecoder.open(context, Uri.parse(uriString))
        if (opened == 0L) throw IllegalStateException("Native MotionCam decoder returned an invalid handle")
        try {
            val count = NativeMcrawDecoder.frameCount(opened)
            if (count <= 0) throw IllegalStateException("MotionCam recording contains no video frames")
            val rawTimestamps = LongArray(count) { index -> NativeMcrawDecoder.frameTimestampNs(opened, index) }
            val timestampOrigin = rawTimestamps.firstOrNull() ?: 0L
            val timestamps = LongArray(count) { index ->
                (rawTimestamps[index] - timestampOrigin).coerceAtLeast(0L)
            }
            handle = opened
            openedUri = uriString
            frameTimestampsNs = timestamps
            durationUsValue = estimateDurationUs(timestamps)
            currentPositionUsValue = timestamps.firstOrNull()?.div(1_000L) ?: 0L
        } catch (error: Exception) {
            NativeMcrawDecoder.close(opened)
            throw error
        }
    }

    private fun decodeFrame(index: Int): Bitmap {
        val decoded = NativeMcrawDecoder.decodeFrame(handle, index, PREVIEW_MAX_EDGE)
        val rotation = runCatching {
            JSONObject(NativeMcrawDecoder.frameMetadataJson(handle, index))
                .optInt("rotationDegrees", 0)
                .let { ((it % 360) + 360) % 360 }
        }.getOrDefault(0)
        if (rotation == 0) return decoded
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    private fun startPlaybackLoop() {
        if (released || playbackJob?.isActive == true || frameTimestampsNs.isEmpty()) return
        playWhenReady = false
        playbackJob = scope.launch {
            _currentState.value = PreviewState.PLAYING
            while (!released && currentFrameIndex < frameTimestampsNs.lastIndex) {
                val nextIndex = currentFrameIndex + 1
                val delayForFrameMs = frameIntervalMs(currentFrameIndex, nextIndex)
                val decodeStartedAt = System.nanoTime()
                try {
                    val frameAndTime = withContext(Dispatchers.IO) {
                        decoderMutex.withLock {
                            if (handle == 0L) throw IllegalStateException("MotionCam decoder is closed")
                            val bitmap = decodeFrame(nextIndex)
                            currentFrameIndex = nextIndex
                            currentPositionUsValue = frameTimestampsNs[nextIndex] / 1_000L
                            bitmap to currentPositionUsValue
                        }
                    }
                    if (released) break
                    _currentBitmap.value = frameAndTime.first
                    _firstFrameRendered.value = true
                    onTimeChanged?.invoke(frameAndTime.second)
                    val decodeElapsedMs = (System.nanoTime() - decodeStartedAt) / 1_000_000L
                    val waitMs = (delayForFrameMs - decodeElapsedMs).coerceAtLeast(0L)
                    if (waitMs > 0L) delay(waitMs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _currentError.value = error.message ?: "MotionCam frame playback failed"
                    _currentState.value = PreviewState.ERROR
                    return@launch
                }
            }
            if (!released && currentFrameIndex >= frameTimestampsNs.lastIndex) {
                currentPositionUsValue = durationUsValue
                onTimeChanged?.invoke(durationUsValue)
                _currentState.value = PreviewState.READY
            }
        }
    }

    private fun frameIntervalMs(from: Int, to: Int): Long {
        if (from !in frameTimestampsNs.indices || to !in frameTimestampsNs.indices) return 33L
        val intervalNs = (frameTimestampsNs[to] - frameTimestampsNs[from]).coerceAtLeast(1L)
        return (intervalNs / 1_000_000.0 / playbackSpeed.coerceIn(0.1f, 4f))
            .toLong().coerceIn(1L, 250L)
    }

    override fun play() {
        if (released || requestedUri == null) return
        playWhenReady = true
        if (frameTimestampsNs.isEmpty()) return
        playbackJob?.cancel()
        if (currentFrameIndex >= frameTimestampsNs.lastIndex) {
            val uri = requestedUri ?: return
            loadMedia(
                uriString = uri,
                useProxy = false,
                sourcePositionMs = 0L,
                evaluatedParams = null,
                presentationTimeUs = 0L,
                forceSeek = true
            )
            return
        }
        startPlaybackLoop()
    }

    override fun pause() {
        playWhenReady = false
        playbackJob?.cancel()
        playbackJob = null
        if (_currentState.value == PreviewState.PLAYING) _currentState.value = PreviewState.READY
    }

    override fun seekTo(timeUs: Long) {
        val uri = requestedUri ?: return
        loadMedia(
            uriString = uri,
            useProxy = false,
            sourcePositionMs = timeUs.coerceAtLeast(0L) / 1_000L,
            evaluatedParams = null,
            presentationTimeUs = timeUs.coerceAtLeast(0L),
            forceSeek = true
        )
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = if (speed.isFinite()) speed.coerceIn(0.1f, 4f) else 1f
    }

    override fun setResolutionFallback(resolutionMode: String) = Unit

    fun clearMedia() {
        if (released) return
        requestedUri = null
        playWhenReady = false
        loadJob?.cancel()
        pause()
        _currentBitmap.value = null
        _currentError.value = null
        _currentState.value = PreviewState.IDLE
        _firstFrameRendered.value = false
        durationUsValue = 0L
        currentPositionUsValue = 0L
        scope.launch(Dispatchers.IO) {
            decoderMutex.withLock {
                if (handle != 0L) NativeMcrawDecoder.close(handle)
                handle = 0L
                openedUri = null
                frameTimestampsNs = LongArray(0)
            }
        }
    }

    override fun release() {
        if (released) return
        released = true
        playWhenReady = false
        loadJob?.cancel()
        playbackJob?.cancel()
        onTimeChanged = null
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanupScope.launch {
            decoderMutex.withLock {
                if (handle != 0L) NativeMcrawDecoder.close(handle)
                handle = 0L
                openedUri = null
                frameTimestampsNs = LongArray(0)
            }
            cleanupScope.cancel()
        }
        scope.cancel()
        _currentBitmap.value = null
        _currentState.value = PreviewState.IDLE
        _firstFrameRendered.value = false
    }

    private fun nearestFrameIndex(timestamps: LongArray, targetNs: Long): Int {
        val search = timestamps.binarySearch(targetNs)
        if (search >= 0) return search
        val insertion = -search - 1
        if (insertion <= 0) return 0
        if (insertion >= timestamps.size) return timestamps.lastIndex
        val earlier = timestamps[insertion - 1]
        val later = timestamps[insertion]
        return if (abs(targetNs - earlier) <= abs(later - targetNs)) insertion - 1 else insertion
    }

    private fun estimateDurationUs(timestamps: LongArray): Long {
        if (timestamps.isEmpty()) return 0L
        val intervals = timestamps.asList().zipWithNext { left, right -> right - left }.filter { it > 0L }.sorted()
        val intervalNs = if (intervals.isEmpty()) 0L else intervals[intervals.size / 2]
        val durationNs = if (timestamps.size > 1) {
            (timestamps.last() - timestamps.first()).coerceAtLeast(0L) + intervalNs
        } else {
            intervalNs.takeIf { it > 0L } ?: 33_333_333L
        }
        return durationNs / 1_000L
    }

    companion object {
        private const val PREVIEW_MAX_EDGE = 960
    }
}
