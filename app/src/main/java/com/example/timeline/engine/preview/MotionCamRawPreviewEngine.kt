package com.example.timeline.engine.preview

import android.content.Context
import android.graphics.Bitmap
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
        // Exposure, white balance, primary wheels, and viewport brightness/contrast/saturation are
        // rendered live; the color-grading screen calls out curves/HSL/LUT and advanced gaps.
        canApplyRealtimeColorGrade = true,
        canApplyBasicAdjustments = true,
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
    private val _currentFrameRevision = MutableStateFlow(0L)
    val currentFrameRevision = _currentFrameRevision.asStateFlow()
    private val previewBitmapBuffers = arrayOfNulls<Bitmap>(2)
    @Volatile private var lastPublishedBitmapBuffer = -1

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
    @Volatile private var previewAdjustmentParams = VideoAdjustmentParams.default()
    private var loadJob: Job? = null
    private var playbackJob: Job? = null
    private var pausedGradeRefreshJob: Job? = null
    @Volatile private var lastPausedGradeRefreshMs = 0L
    private var playWhenReady = false
    @Volatile private var released = false

    override var onTimeChanged: ((Long) -> Unit)? = null
    override var onPlaybackEnded: (() -> Unit)? = null

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
        val wasFirstFrameRendered = _firstFrameRendered.value
        val previousParams = previewAdjustmentParams
        val nextParams = evaluatedParams ?: if (uriChanged) {
            VideoAdjustmentParams.default()
        } else {
            previousParams
        }
        val nativeGradeChanged = !uriChanged && evaluatedParams != null &&
            MotionCamPreviewGradeSupport.hasNativeGradeChanged(previousParams, nextParams)
        val targetPositionMs = sourcePositionMs.coerceAtLeast(0L)
        val currentPositionMs = currentPositionUsValue / 1_000L
        val sameFrameGradeUpdate = !uriChanged && !forceSeek && wasFirstFrameRendered &&
            evaluatedParams != null &&
            abs(currentPositionMs - targetPositionMs) <= GRADE_UPDATE_POSITION_TOLERANCE_MS

        requestedUri = uriString
        previewAdjustmentParams = nextParams
        if (sameFrameGradeUpdate) {
            _currentError.value = null
            if (nativeGradeChanged && playbackJob?.isActive != true && !playWhenReady) {
                requestPausedGradeRefresh(uriString)
            }
            return
        }

        val resumePlayback = playbackJob?.isActive == true || playWhenReady
        if (resumePlayback) playWhenReady = true
        pausedGradeRefreshJob?.cancel()
        pausedGradeRefreshJob = null
        playbackJob?.cancel()
        playbackJob = null
        loadJob?.cancel()
        _currentError.value = null
        if (uriChanged || !_firstFrameRendered.value) {
            _currentState.value = PreviewState.LOADING
            _firstFrameRendered.value = false
            if (uriChanged) clearPreviewFrame()
        }

        loadJob = scope.launch {
            try {
                val frame = withContext(Dispatchers.IO) {
                    decoderMutex.withLock {
                        ensureOpen(uriString)
                        if (frameTimestampsNs.isEmpty()) throw IllegalStateException("MotionCam recording contains no frames")
                        val targetNs = targetPositionMs.coerceAtMost(Long.MAX_VALUE / 1_000_000L) * 1_000_000L
                        val index = nearestFrameIndex(frameTimestampsNs, targetNs)
                        val maximumEdge = if (resumePlayback) PLAYBACK_MAX_EDGE else STILL_MAX_EDGE
                        val bitmap = decodeFrame(index, maximumEdge, previewAdjustmentParams)
                        val timestampUs = frameTimestampsNs[index] / 1_000L
                        currentFrameIndex = index
                        currentPositionUsValue = timestampUs
                        bitmap
                    }
                }
                if (released || requestedUri != uriString) return@launch
                publishFrame(frame)
                _firstFrameRendered.value = true
                _currentError.value = null
                val shouldResume = playWhenReady
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

    override fun updatePreviewGrade(evaluatedParams: VideoAdjustmentParams) {
        if (released) return
        val previousParams = previewAdjustmentParams
        previewAdjustmentParams = evaluatedParams
        if (playbackJob?.isActive != true && !playWhenReady &&
            MotionCamPreviewGradeSupport.hasNativeGradeChanged(previousParams, evaluatedParams)
        ) {
            requestedUri?.let(::requestPausedGradeRefresh)
        }
    }

    private fun requestPausedGradeRefresh(uriString: String) {
        if (released || playbackJob?.isActive == true || playWhenReady) return
        pausedGradeRefreshJob?.cancel()
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val waitMs = (GRADE_REFRESH_INTERVAL_MS - (nowMs - lastPausedGradeRefreshMs).coerceAtLeast(0L))
            .coerceAtLeast(0L)
        pausedGradeRefreshJob = scope.launch {
            try {
                if (waitMs > 0L) delay(waitMs)
                if (released || requestedUri != uriString || playbackJob?.isActive == true ||
                    playWhenReady || !_firstFrameRendered.value
                ) return@launch

                val bitmap = withContext(Dispatchers.IO) {
                    decoderMutex.withLock {
                        if (released || requestedUri != uriString || handle == 0L ||
                            playbackJob?.isActive == true
                        ) return@withLock null
                        val rendered = decodeFrame(currentFrameIndex, GRADE_PREVIEW_MAX_EDGE, previewAdjustmentParams)
                        lastPausedGradeRefreshMs = android.os.SystemClock.elapsedRealtime()
                        rendered
                    }
                } ?: return@launch

                if (released || requestedUri != uriString || playbackJob?.isActive == true || playWhenReady) {
                    return@launch
                }
                publishFrame(bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (released || requestedUri != uriString) return@launch
                _currentError.value = error.message ?: "MotionCam grade preview failed"
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

    private fun decodeFrame(
        index: Int,
        maximumEdge: Int = STILL_MAX_EDGE,
        params: VideoAdjustmentParams = previewAdjustmentParams
    ): Bitmap {
        val bufferIndex = if (lastPublishedBitmapBuffer == 0) 1 else 0
        val bitmap = NativeMcrawDecoder.decodeFrameInto(
            handle = handle,
            index = index,
            maximumEdge = maximumEdge,
            gradeParams = MotionCamPreviewGradeSupport.nativeGradeParams(params),
            reusableBitmap = previewBitmapBuffers[bufferIndex]
        )
        previewBitmapBuffers[bufferIndex] = bitmap
        return bitmap
    }

    /** StateFlow uses value equality, so a separate revision makes reused Bitmap writes observable. */
    private fun publishFrame(bitmap: Bitmap) {
        lastPublishedBitmapBuffer = previewBitmapBuffers.indexOfFirst { it === bitmap }
        _currentBitmap.value = bitmap
        _currentFrameRevision.value += 1L
    }

    private fun clearPreviewFrame() {
        lastPublishedBitmapBuffer = -1
        _currentBitmap.value = null
        _currentFrameRevision.value += 1L
    }

    /** Uses source timestamps as a wall-clock target and skips missed frames instead of slowing the clip. */
    private fun startPlaybackLoop() {
        if (released || playbackJob?.isActive == true || frameTimestampsNs.isEmpty()) return
        playWhenReady = false
        playbackJob = scope.launch {
            _currentState.value = PreviewState.PLAYING
            val playbackStartWallNs = System.nanoTime()
            val playbackStartMediaNs = frameTimestampsNs[currentFrameIndex]
            val durationNs = durationUsValue.coerceAtLeast(0L) * 1_000L

            while (!released) {
                try {
                    val elapsedWallNs = (System.nanoTime() - playbackStartWallNs).coerceAtLeast(0L)
                    val speed = playbackSpeed.coerceIn(0.1f, 4f)
                    val elapsedMediaNs = (elapsedWallNs.toDouble() * speed.toDouble())
                        .coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
                    val targetMediaNs = if (elapsedMediaNs > Long.MAX_VALUE - playbackStartMediaNs) {
                        Long.MAX_VALUE
                    } else {
                        playbackStartMediaNs + elapsedMediaNs
                    }

                    if (targetMediaNs >= durationNs || currentFrameIndex >= frameTimestampsNs.lastIndex) {
                        if (targetMediaNs < durationNs && currentFrameIndex >= frameTimestampsNs.lastIndex) {
                            val remainingNs = durationNs - targetMediaNs
                            delay((remainingNs / 1_000_000.0 / speed).toLong().coerceIn(1L, 50L))
                            continue
                        }
                        currentPositionUsValue = durationUsValue
                        // Clear the active job before callbacks. The timeline may pause this engine
                        // synchronously from either callback when the clip reaches its boundary.
                        playbackJob = null
                        _currentState.value = PreviewState.READY
                        onTimeChanged?.invoke(durationUsValue)
                        onPlaybackEnded?.invoke()
                        return@launch
                    }

                    val targetIndex = nearestFrameIndex(frameTimestampsNs, targetMediaNs)
                    if (targetIndex <= currentFrameIndex) {
                        val nextIndex = (currentFrameIndex + 1).coerceAtMost(frameTimestampsNs.lastIndex)
                        val untilNextNs = (frameTimestampsNs[nextIndex] - targetMediaNs).coerceAtLeast(1L)
                        delay((untilNextNs / 1_000_000.0 / speed).toLong().coerceIn(1L, 50L))
                        continue
                    }

                    val frameAndTime = withContext(Dispatchers.IO) {
                        decoderMutex.withLock {
                            if (handle == 0L) throw IllegalStateException("MotionCam decoder is closed")
                            val bitmap = decodeFrame(targetIndex, PLAYBACK_MAX_EDGE, previewAdjustmentParams)
                            currentFrameIndex = targetIndex
                            currentPositionUsValue = frameTimestampsNs[targetIndex] / 1_000L
                            bitmap to currentPositionUsValue
                        }
                    }
                    if (released) return@launch
                    publishFrame(frameAndTime.first)
                    _firstFrameRendered.value = true
                    onTimeChanged?.invoke(frameAndTime.second)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _currentError.value = error.message ?: "MotionCam frame playback failed"
                    _currentState.value = PreviewState.ERROR
                    return@launch
                }
            }
        }
    }

    override fun play() {
        if (released || requestedUri == null) return
        pausedGradeRefreshJob?.cancel()
        pausedGradeRefreshJob = null
        playWhenReady = true
        if (loadJob?.isActive == true) return
        if (frameTimestampsNs.isEmpty()) return
        playbackJob?.cancel()
        playbackJob = null
        startPlaybackLoop()
    }

    override fun pause() {
        val wasPlaying = playbackJob?.isActive == true
        playWhenReady = false
        playbackJob?.cancel()
        playbackJob = null
        if (_currentState.value == PreviewState.PLAYING) _currentState.value = PreviewState.READY
        if (wasPlaying && _firstFrameRendered.value &&
            MotionCamPreviewGradeSupport.hasNativePreviewAdjustments(previewAdjustmentParams)
        ) {
            requestedUri?.let(::requestPausedGradeRefresh)
        }
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
        pausedGradeRefreshJob?.cancel()
        pausedGradeRefreshJob = null
        pause()
        clearPreviewFrame()
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
                previewBitmapBuffers.fill(null)
                lastPublishedBitmapBuffer = -1
            }
        }
    }

    override fun release() {
        if (released) return
        released = true
        playWhenReady = false
        loadJob?.cancel()
        playbackJob?.cancel()
        pausedGradeRefreshJob?.cancel()
        onTimeChanged = null
        onPlaybackEnded = null
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanupScope.launch {
            decoderMutex.withLock {
                if (handle != 0L) NativeMcrawDecoder.close(handle)
                handle = 0L
                openedUri = null
                frameTimestampsNs = LongArray(0)
                previewBitmapBuffers.fill(null)
                lastPublishedBitmapBuffer = -1
            }
            cleanupScope.cancel()
        }
        scope.cancel()
        clearPreviewFrame()
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
        private const val STILL_MAX_EDGE = 960
        private const val PLAYBACK_MAX_EDGE = 480
        private const val GRADE_PREVIEW_MAX_EDGE = 480
        private const val GRADE_UPDATE_POSITION_TOLERANCE_MS = 250L
        private const val GRADE_REFRESH_INTERVAL_MS = 40L
    }
}
