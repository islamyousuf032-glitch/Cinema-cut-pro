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

class TimelinePreviewController(private val context: Context) {

    var previewEngine: PreviewEngine = createEngine(context, PreviewEngineType.AUTO)
        private set

    private fun createEngine(context: Context, type: PreviewEngineType): PreviewEngine {
        return PreviewEngineFactory.createEngine(context, type)
    }

    private var activeProject: TimelineProject? = null
    private var lastPlayheadFrame: Long = 0L

    private val _activeClip = MutableStateFlow<TimelineClip?>(null)
    val activeClip: StateFlow<TimelineClip?> = _activeClip.asStateFlow()

    private val _activeAsset = MutableStateFlow<MediaAsset?>(null)
    val activeAsset: StateFlow<MediaAsset?> = _activeAsset.asStateFlow()

    private val _engineTypeFlow = MutableStateFlow(previewEngine.engineType)
    val engineTypeFlow: StateFlow<PreviewEngineType> = _engineTypeFlow.asStateFlow()
    
    // Callback to notify timeline ViewModel to update its playhead
    var onPlayheadAdvanced: ((Long) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playheadJob: Job? = null

    private var lastEmittedTimeMs: Long = 0

    init {
        // startPlayheadMonitor() removed
        setupEngineListeners(previewEngine)
    }

    private fun setupEngineListeners(engine: PreviewEngine) {
        engine.onTimeChanged = { currentUs ->
            if (engine.engineType != PreviewEngineType.STILL_FRAME) {
                // Throttle updates to ~30fps -> ~33ms
                val now = System.currentTimeMillis()
                val updateEnd = if (now - lastEmittedTimeMs > 30) true else false

                val clip = activeClip.value
                val proj = activeProject
                if (clip != null && proj != null && currentUs >= 0L) {
                    val rat = proj.settings.getFpsRational()
                    val fps = rat.numerator.toDouble() / rat.denominator.toDouble()

                    val sourceInUs = TimelineFrameResolver.convertFrameToMicroseconds(clip.sourceIn, rat.numerator, rat.denominator)
                    val offsetUs = currentUs - sourceInUs

                    val offsetFrames = (offsetUs * fps / 1_000_000.0).toLong()
                    val projectPlayheadFrame = clip.timelineStart + offsetFrames

                    val newPlayhead = Math.max(clip.timelineStart, Math.min(clip.timelineEnd - 1, projectPlayheadFrame))

                    if (updateEnd && newPlayhead != lastPlayheadFrame) {
                        lastPlayheadFrame = newPlayhead
                        lastEmittedTimeMs = now
                        onPlayheadAdvanced?.invoke(newPlayhead)
                    }

                    // Stop if we reach clip end. Enforce clip boundaries precisely
                    if (projectPlayheadFrame >= clip.timelineEnd) {
                        engine.pause()
                        lastPlayheadFrame = clip.timelineEnd - 1
                        onPlayheadAdvanced?.invoke(lastPlayheadFrame)
                    }
                }
            }
        }
    }

    fun setEngineType(type: PreviewEngineType) {
        if (previewEngine.engineType == type && type != PreviewEngineType.AUTO) return
        
        val wasPlaying = previewEngine.isPlaying
        val oldEngine = previewEngine
        
        try {
            val newEngine = createEngine(context, type)
            // Load clip first
            if (activeProject != null && activeClip.value != null && activeAsset.value != null) {
                val clip = activeClip.value!!
                val asset = activeAsset.value!!
                val rat = activeProject!!.settings.getFpsRational()
                val sourceFrame = TimelineFrameResolver.resolveSourceFrame(lastPlayheadFrame, clip)
                val sourcePositionUs = TimelineFrameResolver.convertFrameToMicroseconds(sourceFrame, rat.numerator, rat.denominator)
                val sourcePositionMs = sourcePositionUs / 1000
                val (playableUri, useProxy) = PreviewSourceResolver.resolvePlayableUri(asset, preferProxy = true)
                    .let { (uri, isProxy) -> uri to isProxy }
                val evaluatedParams = GradeEvaluationEngine.evaluateFrame(activeProject!!, lastPlayheadFrame)
                
                if (!playableUri.isNullOrBlank()) {
                    newEngine.loadMedia(playableUri, useProxy, sourcePositionMs, evaluatedParams, sourcePositionUs)
                }
            }
            // Once ready, switch
            previewEngine = newEngine
            setupEngineListeners(newEngine)
            _engineTypeFlow.value = newEngine.engineType
            
            if (wasPlaying) {
                newEngine.play()
            }
            // Now release old engine safely
            scope.launch {
                delay(200) // gentle delay to prevent black flash
                oldEngine.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("TimelinePreviewController", "Failed to switch engine to \$type", e)
        }
    }



    fun loadProject(project: TimelineProject) {
        activeProject = project
    }

    fun seekToTimelineFrame(project: TimelineProject, frame: Long) {
        if (activeProject?.id != project.id) {
            loadProject(project)
        } else {
            activeProject = project
        }
        lastPlayheadFrame = frame
        loadClipAtPlayhead(project, frame)
    }

    private fun loadClipAtPlayhead(project: TimelineProject, playheadFrame: Long) {
        android.util.Log.d("VIEWPORT_PREVIEW", "resolving frame=$playheadFrame")
        val topClip = TimelineFrameResolver.getTopmostVisibleVideoClip(project, playheadFrame)
        if (topClip == null) {
            _activeClip.value = null
            _activeAsset.value = null
            val eng = previewEngine
            if (eng is Media3FallbackPreviewEngine) {
                eng.clearMedia()
            }
            return
        }
        
        android.util.Log.d("VIEWPORT_PREVIEW", "found clipId=${topClip.id}")

        val asset = project.mediaAssets.find { it.assetId == topClip.mediaId }
        val prevClipId = _activeClip.value?.id

        _activeClip.value = topClip
        _activeAsset.value = asset

        if (asset == null) {
            val eng = previewEngine
            if (eng is Media3FallbackPreviewEngine) {
                eng.clearMedia()
            }
            return
        }

        android.util.Log.d("VIEWPORT_PREVIEW", "assetId=${asset.assetId}")

        val sourceFrame = TimelineFrameResolver.resolveSourceFrame(playheadFrame, topClip)
        val rat = project.settings.getFpsRational()
        val sourcePositionMs = TimelineFrameResolver.convertFrameToMicroseconds(sourceFrame, rat.numerator, rat.denominator) / 1000
        val sourcePositionUs = sourcePositionMs * 1000

        val evaluatedParams = com.example.timeline.engine.GradeEvaluationEngine.evaluateFrame(project, playheadFrame)

        // Prefer a ready proxy for editor playback. The proxy is generated at a predictable
        // preview resolution and retains the source frame rate, so timeline timecode/audio sync
        // continue to use the same source-time mapping.
        val (uriToUse, useProxy) = PreviewSourceResolver.resolvePlayableUri(asset, preferProxy = true)

        if (uriToUse.isNullOrBlank()) {
            val eng = previewEngine
            if (eng is Media3FallbackPreviewEngine) eng.clearMedia()
        } else {
            previewEngine.loadMedia(uriToUse, useProxy, sourcePositionMs, evaluatedParams, sourcePositionUs)
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
        val uriStr = PreviewSourceResolver.resolvePlayableUri(asset, false).first ?: return null
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
            seekToTimelineFrame(project, lastPlayheadFrame)
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
