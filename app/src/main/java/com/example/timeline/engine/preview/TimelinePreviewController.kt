package com.example.timeline.engine.preview

import android.content.Context
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.media.MediaAsset
import com.example.timeline.engine.preview.PreviewEngineSettings
import com.example.timeline.engine.preview.PreviewEngineType
import com.example.timeline.engine.preview.PreviewState
import com.example.timeline.engine.preview.PreviewEngine
import com.example.timeline.engine.preview.PreviewEngineFactory
import com.example.model.adjustments.VideoAdjustmentParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel

class TimelinePreviewController(private val context: Context) {

    var previewEngine: PreviewEngine = createEngine(context, PreviewEngineType.AUTO)
        private set

    private fun createEngine(context: Context, type: PreviewEngineType): PreviewEngine {
        return PreviewEngineFactory.createEngine(context, type)
    }

    private var activeProject: TimelineProject? = null
    private var lastPlayheadFrame: Long = 0L
    @Volatile private var colorGradeBypassed: Boolean = false
    @Volatile private var preferProxyPlayback: Boolean = true

    private fun evaluatedPreviewParams(project: TimelineProject, frame: Long): VideoAdjustmentParams =
        if (colorGradeBypassed) VideoAdjustmentParams.default()
        else com.example.timeline.engine.GradeEvaluationEngine.evaluateFrame(project, frame)

    /** Preview-only grade bypass; it never edits or saves the project. */
    fun setColorGradeBypassed(bypassed: Boolean) {
        if (colorGradeBypassed == bypassed) return
        colorGradeBypassed = bypassed
        activeProject?.let { project ->
            seekToTimelineFrame(project, lastPlayheadFrame, forceSeek = false)
        }
    }

    /** Selects the source for smooth (proxy-first) versus color-accurate (original-first) preview. */
    fun setPreferProxyPlayback(preferProxy: Boolean) {
        if (preferProxyPlayback == preferProxy) return
        preferProxyPlayback = preferProxy
        activeProject?.let { project ->
            seekToTimelineFrame(project, lastPlayheadFrame, forceSeek = true)
        }
    }

    private val _activeClip = MutableStateFlow<TimelineClip?>(null)
    val activeClip: StateFlow<TimelineClip?> = _activeClip.asStateFlow()

    private val _activeAsset = MutableStateFlow<MediaAsset?>(null)
    val activeAsset: StateFlow<MediaAsset?> = _activeAsset.asStateFlow()

    private val _activeSourceIsProxy = MutableStateFlow(false)
    /** True only when the engine was actually given the READY proxy URI. */
    val activeSourceIsProxy: StateFlow<Boolean> = _activeSourceIsProxy.asStateFlow()
    private val proxyPlaybackFailedAssetIds = mutableSetOf<String>()

    private fun resolveSource(asset: MediaAsset): PreviewMediaSource? {
        if (asset.proxyStatus != com.example.timeline.media.ProxyStatus.READY || asset.proxyInfo == null) {
            proxyPlaybackFailedAssetIds.remove(asset.assetId)
        }
        val forceOriginal = asset.assetId in proxyPlaybackFailedAssetIds
        return PreviewSourceResolver.resolveSource(
            asset = asset,
            preferProxy = preferProxyPlayback,
            forceOriginal = forceOriginal
        ).also { source ->
            _activeSourceIsProxy.value = source?.isProxy == true
        }
    }

    /**
     * Switches away from a proxy that actually failed, without marking the original media as bad.
     * Returns false when the current source is not the active asset's proxy.
     */
    fun fallbackFromProxy(assetId: String): Boolean {
        val asset = _activeAsset.value ?: return false
        val project = activeProject ?: return false
        if (asset.assetId != assetId || !_activeSourceIsProxy.value) return false
        proxyPlaybackFailedAssetIds += assetId
        loadClipAtPlayhead(project, lastPlayheadFrame, forceSeek = true)
        return true
    }

    private val _engineTypeFlow = MutableStateFlow(previewEngine.engineType)
    val engineTypeFlow: StateFlow<PreviewEngineType> = _engineTypeFlow.asStateFlow()
    
    // Callbacks keep the timeline synchronized without a second UI-thread polling loop.
    var onPlayheadAdvanced: ((Long) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    private var pendingBoundaryClipId: String? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playheadJob: Job? = null
    private val pendingEngineReleases = mutableSetOf<PreviewEngine>()
    private var released = false

    private var lastEmittedTimeMs: Long = 0

    init {
        // startPlayheadMonitor() removed
        setupEngineListeners(previewEngine)
    }

    private fun setupEngineListeners(engine: PreviewEngine) {
        lastEmittedTimeMs = 0L
        engine.onTimeChanged = { currentUs ->
            if (engine.engineType != PreviewEngineType.STILL_FRAME && currentUs >= 0L) {
                val now = System.currentTimeMillis()
                val clip = activeClip.value
                val project = activeProject
                if (clip != null && project != null) {
                    val rate = project.settings.getFpsRational()
                    val fps = rate.numerator.toDouble() / rate.denominator.toDouble()
                    val sourceInUs = TimelineFrameResolver.convertFrameToMicroseconds(
                        clip.sourceIn,
                        rate.numerator,
                        rate.denominator
                    )
                    val offsetUs = currentUs - sourceInUs
                    val offsetFrames = (offsetUs * fps / 1_000_000.0).toLong()
                    val projectFrame = clip.timelineStart + offsetFrames
                    val boundedFrame = projectFrame.coerceIn(clip.timelineStart, clip.timelineEnd - 1)

                    if (now - lastEmittedTimeMs >= 30L && boundedFrame != lastPlayheadFrame) {
                        lastPlayheadFrame = boundedFrame
                        lastEmittedTimeMs = now
                        onPlayheadAdvanced?.invoke(boundedFrame)
                    }

                    val playbackWasActive = engine.isPlaying || engine.currentState.value == PreviewState.PLAYING
                    if (projectFrame >= clip.timelineEnd && playbackWasActive && pendingBoundaryClipId != clip.id) {
                        val nextFrame = clip.timelineEnd
                        val nextClip = TimelineFrameResolver.getTopmostVisibleVideoClip(project, nextFrame)
                        pendingBoundaryClipId = clip.id
                        if (nextClip == null) {
                            engine.pause()
                            lastPlayheadFrame = clip.timelineEnd - 1
                            onPlayheadAdvanced?.invoke(lastPlayheadFrame)
                            onPlaybackEnded?.invoke()
                            pendingBoundaryClipId = null
                        } else {
                            lastPlayheadFrame = nextFrame
                            onPlayheadAdvanced?.invoke(nextFrame)
                            scope.launch {
                                if (previewEngine === engine && activeProject?.id == project.id) {
                                    seekToTimelineFrame(project, nextFrame)
                                    if (previewEngine === engine && activeClip.value?.id == nextClip.id) {
                                        engine.play()
                                    }
                                }
                                pendingBoundaryClipId = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun releasePendingEngine(engine: PreviewEngine) {
        if (pendingEngineReleases.remove(engine)) {
            engine.onTimeChanged = null
            engine.release()
        }
    }

    fun setEngineType(type: PreviewEngineType) {
        if (released) return
        val requestedEngine = resolveRequestedPreviewEngine(type)
        if (previewEngine.engineType == requestedEngine) return

        val wasPlaying = previewEngine.isPlaying
        val oldEngine = previewEngine
        var candidateEngine: PreviewEngine? = null

        try {
            val newEngine = createEngine(context, type)
            candidateEngine = newEngine
            // Load the active clip before switching the PlayerView to avoid an empty source flash.
            val project = activeProject
            val clip = activeClip.value
            val asset = activeAsset.value
            if (project != null && clip != null && asset != null) {
                val rate = project.settings.getFpsRational()
                val sourceFrame = TimelineFrameResolver.resolveSourceFrame(lastPlayheadFrame, clip)
                val sourcePositionUs = TimelineFrameResolver.convertFrameToMicroseconds(
                    sourceFrame,
                    rate.numerator,
                    rate.denominator
                )
                val sourcePositionMs = sourcePositionUs / 1_000L
                val source = resolveSource(asset)
                val evaluatedParams = evaluatedPreviewParams(project, lastPlayheadFrame)

                if (source != null) {
                    newEngine.setVolume(clip.volume)
                    newEngine.setSource(
                        source = source,
                        sourcePositionMs = sourcePositionMs,
                        evaluatedParams = evaluatedParams,
                        presentationTimeUs = sourcePositionUs,
                        forceSeek = true
                    )
                }
            } else {
                _activeSourceIsProxy.value = false
            }

            setupEngineListeners(newEngine)
            previewEngine = newEngine
            candidateEngine = null
            _engineTypeFlow.value = newEngine.engineType

            if (wasPlaying) newEngine.play()

            // Keep the old surface alive for one short handoff; the owner tracks it so a scope
            // cancellation before this coroutine starts cannot leak the old player.
            pendingEngineReleases += oldEngine
            scope.launch {
                try {
                    delay(200)
                } finally {
                    releasePendingEngine(oldEngine)
                }
            }
        } catch (e: Exception) {
            candidateEngine?.release()
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("TimelinePreviewController", "Failed to switch engine to $type", e)
            }
        }
    }

    fun loadProject(project: TimelineProject) {
        activeProject = project
    }

    fun seekToTimelineFrame(project: TimelineProject, frame: Long, forceSeek: Boolean = true) {
        if (activeProject?.id != project.id) {
            loadProject(project)
        } else {
            activeProject = project
        }
        lastPlayheadFrame = frame
        loadClipAtPlayhead(project, frame, forceSeek)
    }

    private fun loadClipAtPlayhead(project: TimelineProject, playheadFrame: Long, forceSeek: Boolean) {
        val topClip = TimelineFrameResolver.getTopmostVisibleVideoClip(project, playheadFrame)
        if (topClip == null) {
            _activeClip.value = null
            _activeAsset.value = null
            _activeSourceIsProxy.value = false
            val eng = previewEngine
            if (eng is Media3FallbackPreviewEngine) {
                eng.clearMedia()
            }
            return
        }
        
        val asset = project.mediaAssets.find { it.assetId == topClip.mediaId }
        val prevClipId = _activeClip.value?.id

        _activeClip.value = topClip
        _activeAsset.value = asset

        if (asset == null) {
            _activeSourceIsProxy.value = false
            val eng = previewEngine
            if (eng is Media3FallbackPreviewEngine) {
                eng.clearMedia()
            }
            return
        }

        val sourceFrame = TimelineFrameResolver.resolveSourceFrame(playheadFrame, topClip)
        val rat = project.settings.getFpsRational()
        val sourcePositionMs = TimelineFrameResolver.convertFrameToMicroseconds(sourceFrame, rat.numerator, rat.denominator) / 1000
        val sourcePositionUs = sourcePositionMs * 1000

        val evaluatedParams = evaluatedPreviewParams(project, playheadFrame)

        // Prefer a ready proxy for editor playback. Source MIME is passed through so Media3 can
        // select the right extractor for opaque SAF/content URIs and report useful failures.
        val source = resolveSource(asset)

        if (source == null) {
            val engine = previewEngine
            if (engine is Media3FallbackPreviewEngine) engine.clearMedia()
        } else {
            previewEngine.setVolume(topClip.volume)
            previewEngine.setSource(
                source = source,
                sourcePositionMs = sourcePositionMs,
                evaluatedParams = evaluatedParams,
                presentationTimeUs = sourcePositionUs,
                forceSeek = forceSeek || prevClipId != topClip.id
            )
        }
    }

    fun play() {
        val clip = _activeClip.value
        if (clip != null && lastPlayheadFrame >= clip.timelineEnd - 1) {
            seekToTimelineFrame(activeProject!!, clip.timelineStart)
        }
        previewEngine.play()
    }

    fun pause() {
        previewEngine.pause()
    }

    fun togglePlayPause() {
        if (previewEngine.isPlaying) pause() else play()
    }

    fun stop() {
        previewEngine.pause()
        val eng = previewEngine
        if (eng is Media3FallbackPreviewEngine) eng.clearMedia()
    }
    
    suspend fun extractCurrentFrameBitmap(): android.graphics.Bitmap? {
        val clip = _activeClip.value ?: return null
        val asset = _activeAsset.value ?: return null
        val uriStr = PreviewSourceResolver.resolvePlayableUri(asset, false, forceOriginal = true).first ?: return null
        val rat = activeProject?.settings?.getFpsRational() ?: return null
        val sourceFrame = TimelineFrameResolver.resolveSourceFrame(lastPlayheadFrame, clip)
        val sourceTimeUs = TimelineFrameResolver.convertFrameToMicroseconds(sourceFrame, rat.numerator, rat.denominator)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, android.net.Uri.parse(uriStr))
                mmr.getFrameAtTime(sourceTimeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (e: Exception) {
                null
            } finally {
                mmr.release()
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        onPlayheadAdvanced = null
        onPlaybackEnded = null
        playheadJob?.cancel()
        playheadJob = null
        scope.cancel()
        pendingEngineReleases.toList().forEach(::releasePendingEngine)
        previewEngine.onTimeChanged = null
        previewEngine.release()
    }

    fun onTimelineChanged(project: TimelineProject) {
        seekToTimelineFrame(project, lastPlayheadFrame)
    }

    fun onMediaAssetUpdated(project: TimelineProject) {
        seekToTimelineFrame(project, lastPlayheadFrame)
    }

    fun onProjectChanged(project: TimelineProject) {
        if (activeProject == null || activeProject?.id != project.id) {
            loadProject(project)
            seekToTimelineFrame(project, 0L)
        } else {
            loadProject(project)
            // A grade-only project edit should update effect uniforms without disrupting
            // active playback. Explicit timeline scrubs still use forceSeek=true.
            seekToTimelineFrame(project, lastPlayheadFrame, forceSeek = false)
        }
    }

    fun updatePlayheadFromPlayer(
        onUpdateTimelineFrame: (Long, Long, Long) -> Unit,
        onPlaybackEnded: () -> Unit = {}
    ) {
        if (previewEngine.currentState.value == PreviewState.LOADING || previewEngine.currentState.value == PreviewState.IDLE) {
            return
        }
        val clip = _activeClip.value
        if (clip == null) {
            onPlaybackEnded()
            return
        }

        val playerPosMs = previewEngine.currentPositionUs / 1000
        
        val rat = activeProject?.settings?.getFpsRational()
        if (rat == null) return
        
        val fpsNum = rat.numerator
        val fpsDen = rat.denominator
        
        fun sourcePositionToTimelineFrame(
            positionMs: Long,
            clipTimelineStartFrame: Long,
            clipSourceInFrame: Long
        ): Long {    
            val currentSourceFrame = (positionMs / 1000.0 * fpsNum / fpsDen).toLong()
            return clipTimelineStartFrame + (currentSourceFrame - clipSourceInFrame)
        }
        
        val newProjFrame = sourcePositionToTimelineFrame(
            playerPosMs, clip.timelineStart, clip.sourceIn
        )
        // only callback if valid
        if (newProjFrame >= clip.timelineStart && newProjFrame < clip.timelineEnd) {
             val changed = newProjFrame != lastPlayheadFrame
             lastPlayheadFrame = newProjFrame
             onUpdateTimelineFrame(playerPosMs, clip.timelineStart, clip.sourceIn)
             if (changed) {
                 onPlayheadAdvanced?.invoke(newProjFrame)
             }
        } else {
             // Reached end of clip, need to seek to next clip...
             if (newProjFrame >= clip.timelineEnd) {
                 activeProject?.let { p ->
                     val nextClip = TimelineFrameResolver.getTopmostVisibleVideoClip(p, clip.timelineEnd)
                     if (nextClip == null) {
                         lastPlayheadFrame = clip.timelineEnd - 1
                         onPlayheadAdvanced?.invoke(lastPlayheadFrame)
                         seekToTimelineFrame(p, clip.timelineEnd - 1)
                         pause()
                         onPlaybackEnded()
                     } else {
                         lastPlayheadFrame = clip.timelineEnd
                         onPlayheadAdvanced?.invoke(lastPlayheadFrame)
                         seekToTimelineFrame(p, clip.timelineEnd)
                         play()
                     }
                 }
             } else if (newProjFrame < clip.timelineStart) {
                 // Ignore stale positions from before the clip starts
                 // They will resolve once the player catches up
             }
        }
    }
}
