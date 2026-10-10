package com.example.timeline.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timeline.core.*
import com.example.timeline.core.transform.ClipTransform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

import android.net.Uri
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MediaAnalyzer
import com.example.timeline.media.AndroidMediaAnalyzer
import com.example.timeline.media.ImportWorkManager
import com.example.timeline.media.ProxyGenerationWorker
import com.example.timeline.media.PreviewStatus
import com.example.timeline.media.ProxyStatus
import com.example.timeline.media.MediaErrorStatus
import com.example.timeline.media.ProxyInfo
import kotlinx.coroutines.launch

class TimelineViewModel(context: Context) : ViewModel() {
    private val historyManager = TimelineHistoryManager()
    private val mediaAnalyzer: MediaAnalyzer = AndroidMediaAnalyzer(context)
    private val importWorkManager = ImportWorkManager(context)
    private val workManager = WorkManager.getInstance(context)
        
    private val storage = ProjectStorage(File(context.filesDir, "timeline_projects"))
    private val versionManager = ProjectVersionManager(storage)

    private val _proxyProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val proxyProgress: StateFlow<Map<String, Float>> = _proxyProgress.asStateFlow()
    private val activeProxyJobs = mutableMapOf<String, UUID>()
    private val autosaveManager = AutosaveManager(
        scope = viewModelScope,
        versionManager = versionManager,
        storage = storage,
        getMetadata = { p -> 
            ProjectMetadata(
                id = p.id,
                name = p.name,
                createdDate = System.currentTimeMillis(),
                modifiedDate = System.currentTimeMillis(),
                appVersion = "1.0",
                schemaVersion = ProjectMigration.CURRENT_SCHEMA_VERSION
            )
        }
    )

    private val contextRef = context.applicationContext

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _playheadFrame = MutableStateFlow(0L)
    val playheadFrame: StateFlow<Long> = _playheadFrame.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun togglePlay() {
        _isPlaying.value = !_isPlaying.value
    }

    fun pausePlay() {
        _isPlaying.value = false
    }

    fun syncPlayheadFromPlayer(timeMs: Long, clipTimelineStart: Long, clipSourceIn: Long) {
        val project = _uiState.value.project
        val currentSourceFrame = (timeMs / 1000.0 * project.settings.getFpsRational().toDouble()).toLong()
        val timelineFrame = clipTimelineStart + (currentSourceFrame - clipSourceIn)
        _playheadFrame.value = Math.max(0, timelineFrame)
    }

    fun getActiveVideoClipAndAsset(): Pair<TimelineClip, MediaAsset>? {
        val state = _uiState.value
        val playhead = _playheadFrame.value
        
        // Find topmost video track with a clip at playhead
        for (track in state.project.tracks.filter { it.type == TrackType.VIDEO }.reversed()) {
            val clip = track.clips.find { it.timelineStart <= playhead && (it.timelineStart + (it.sourceOut - it.sourceIn)) > playhead }
            if (clip != null) {
                val asset = state.project.mediaAssets.find { it.assetId == clip.mediaId }
                if (asset != null) return Pair(clip, asset)
            }
        }
        return null
    }

    fun markAssetPlaybackFailed(assetId: String) {
        val stateNow = _uiState.value
        val updatedAssets = stateNow.project.mediaAssets.map { m ->
            if (m.assetId == assetId && m.previewStatus == PreviewStatus.NOT_TESTED) {
                m.copy(previewStatus = PreviewStatus.DIRECT_FAILED_RUNTIME)
            } else m
        }
        updateState(stateNow.project.copy(mediaAssets = updatedAssets))
    }

    fun loadProject(project: TimelineProject) {
        historyManager.clear()
        _uiState.update { 
            it.copy(
                project = project,
                showProjectSetup = false,
                selectedClipId = null,
                canUndo = false,
                canRedo = false
            )
        }
        autosaveManager.markDirty(project)
    }

    fun handlePickedUris(uris: List<Uri>) {
        uris.forEach { uri -> 
            android.util.Log.d("MEDIA_IMPORT", "uri received: $uri")
            enqueueMediaAnalysis(uri) 
        }
    }

    fun enqueueMediaAnalysis(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val hasPermission = com.example.timeline.media.ImportPermissionManager.persistPermission(contextRef, uri)
                android.util.Log.d("MEDIA_IMPORT", "permission persisted: $hasPermission")
                val projectId = _uiState.value.project.id
                var initialAsset = com.example.timeline.media.DocumentMediaReader.createAnalyzingAsset(contextRef, uri, projectId)?.copy(
                    persistedUriPermission = hasPermission,
                    previewStatus = PreviewStatus.NOT_TESTED
                )
                
                if (initialAsset != null) {
                    android.util.Log.d("MEDIA_IMPORT", "media asset created: ${initialAsset.assetId}")
                    val currentProject = _uiState.value.project
                    
                    val isVideo = initialAsset.mimeType.startsWith("video/")
                    val prefix = if (isVideo) "Video " else "Audio "
                    val currentCount = currentProject.mediaAssets.count { it.displayName.startsWith(prefix) }
                    val newName = "$prefix${currentCount + 1}"
                    
                    val updatedMetadata = initialAsset.metadata.copy(originalFilename = initialAsset.displayName)
                    initialAsset = initialAsset.copy(displayName = newName, metadata = updatedMetadata)
                    
                    
                    // Prevent duplicate import of same URI at same time
                    if (currentProject.mediaAssets.none { it.originalUriString == uri.toString() && it.mediaErrorStatus == MediaErrorStatus.NONE && it.mediaAvailabilityStatus != com.example.timeline.media.MediaAvailabilityStatus.MISSING_NEEDS_RELINK }) {
                        val p2 = currentProject.copy(mediaAssets = currentProject.mediaAssets + initialAsset)
                        updateState(p2, markDirty = false)
                        
                        val localPath = copyMediaToLocalStore(uri, initialAsset.assetId, initialAsset.displayName, projectId)
                        
                        initialAsset = initialAsset.copy(
                            localOriginalUriString = localPath,
                            mediaAvailabilityStatus = if (localPath != null) com.example.timeline.media.MediaAvailabilityStatus.AVAILABLE_LOCAL_COPY else com.example.timeline.media.MediaAvailabilityStatus.AVAILABLE_ORIGINAL_URI,
                            previewStatus = PreviewStatus.NOT_TESTED
                        )
                        updateState(_uiState.value.project.copy(mediaAssets = _uiState.value.project.mediaAssets.map { if (it.assetId == initialAsset.assetId) initialAsset else it }), markDirty = false)

                        val uriToAnalyze = if (localPath != null) Uri.parse(localPath) else uri
                        val finalAsset = mediaAnalyzer.analyze(initialAsset, uriToAnalyze)
                        android.util.Log.d("MEDIA_IMPORT", "metadata analyzed: ${finalAsset.previewStatus}")
                        val stateNow = _uiState.value
                        
                        val updatedList = stateNow.project.mediaAssets.map { if (it.assetId == finalAsset.assetId) finalAsset else it }
                        val pFinal = stateNow.project.copy(mediaAssets = updatedList)
                        android.util.Log.d("MEDIA_IMPORT", "asset added to state: ${finalAsset.assetId}")
                        
                        val cmd = SnapshotCommand("Import Media", currentProject, pFinal, emptyList())
                        historyManager.pushCommand(cmd)
                        updateState(pFinal)
                        
                        if (finalAsset.mediaErrorStatus == MediaErrorStatus.NONE && finalAsset.mediaAvailabilityStatus != com.example.timeline.media.MediaAvailabilityStatus.MISSING_NEEDS_RELINK) {
                            android.util.Log.d("MEDIA_IMPORT", "calling addMediaAssetToTimeline for ${finalAsset.displayName}")
                            addClipFromMedia(finalAsset)
                        }
                        
                        if (finalAsset.proxyStatus == ProxyStatus.REQUIRED) {
                            generateProxy(finalAsset.assetId)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun copyMediaToLocalStore(uri: Uri, assetId: String, displayName: String, projectId: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mediaDir = File(contextRef.filesDir, "timeline_projects/$projectId/media/originals")
                if (!mediaDir.exists()) {
                    mediaDir.mkdirs()
                }
                
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(contextRef.contentResolver.getType(uri)) ?: "mp4"
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val outputFile = File(mediaDir, "${assetId}_${safeName}")
                
                if (!outputFile.exists()) {
                    contextRef.contentResolver.openInputStream(uri)?.use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                "file://${outputFile.absolutePath}"
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    private fun proxyDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return sourceWidth to sourceHeight
        val maxSide = 1280
        if (sourceWidth <= maxSide && sourceHeight <= maxSide) return sourceWidth to sourceHeight

        return if (sourceWidth >= sourceHeight) {
            val scaledHeight = (sourceHeight.toFloat() / sourceWidth * maxSide).toInt().let { height ->
                if (height % 2 == 0) height else height - 1
            }.coerceAtLeast(2)
            maxSide to scaledHeight
        } else {
            val scaledWidth = (sourceWidth.toFloat() / sourceHeight * maxSide).toInt().let { width ->
                if (width % 2 == 0) width else width - 1
            }.coerceAtLeast(2)
            scaledWidth to maxSide
        }
    }

    fun generateProxyForAsset(asset: MediaAsset) {
        val workId = importWorkManager.startProxyJob(asset)
        activeProxyJobs[asset.assetId] = workId
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo != null) {
                    val progress = workInfo.progress.getInt(ProxyGenerationWorker.KEY_PROGRESS, 0)
                    _proxyProgress.value = _proxyProgress.value.toMutableMap().apply {
                        this[asset.assetId] = progress / 100f
                    }
                    
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        val outputUri = workInfo.outputData.getString(ProxyGenerationWorker.KEY_OUTPUT_URI)
                        if (outputUri != null) {
                            val stateNow = _uiState.value
                            val updatedAssets = stateNow.project.mediaAssets.map { m ->
                                if (m.assetId == asset.assetId) {
                                    val (proxyWidth, proxyHeight) = proxyDimensions(m.metadata.width, m.metadata.height)
                                    m.copy(
                                        proxyStatus = ProxyStatus.READY,
                                        proxyInfo = ProxyInfo(
                                            uriString = outputUri,
                                            width = proxyWidth,
                                            height = proxyHeight,
                                            frameRate = m.metadata.estimatedFrameRate?.fpsAsFloat ?: 30f,
                                            isProxyGenerated = true,
                                            proxyFormat = "mp4",
                                            proxyMimeType = "video/avc"
                                        )
                                    )
                                } else m
                            }
                            updateState(stateNow.project.copy(mediaAssets = updatedAssets))
                        }
                    } else if (workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED) {
                        val errorMsg = workInfo.outputData.getString(ProxyGenerationWorker.KEY_ERROR_MSG) ?: workInfo.progress.getString(ProxyGenerationWorker.KEY_ERROR_MSG) ?: "Proxy generation failed."
                        val stateNow = _uiState.value
                        val updatedAssets = stateNow.project.mediaAssets.map { m ->
                            if (m.assetId == asset.assetId) {
                                m.copy(
                                    proxyStatus = ProxyStatus.FAILED,
                                    errorMessage = errorMsg
                                )
                            } else m
                        }
                        updateState(stateNow.project.copy(mediaAssets = updatedAssets))
                    }
                }
            }
        }
    }
    
    fun retryMediaAsset(assetId: String) {
        val asset = _uiState.value.project.mediaAssets.find { it.assetId == assetId } ?: return
        
        var isMissing = true
        var targetUriToAnalyze: Uri? = null
        if (asset.localOriginalUriString != null) {
            val file = java.io.File(Uri.parse(asset.localOriginalUriString).path ?: "")
            if (file.exists() && file.length() > 0) {
                isMissing = false
                targetUriToAnalyze = Uri.parse(asset.localOriginalUriString)
            }
        }
        
        if (isMissing) {
            val uri = Uri.parse(asset.originalUriString)
            if (uri.scheme == "file") {
                 if (java.io.File(uri.path ?: "").exists()) {
                     isMissing = false
                     targetUriToAnalyze = uri
                 }
            } else {
                 try {
                     val cursor = contextRef.contentResolver.query(uri, null, null, null, null)
                     if (cursor != null && cursor.moveToFirst()) {
                         isMissing = false
                         targetUriToAnalyze = uri
                     }
                     cursor?.close()
                 } catch (e: Exception) {}
            }
        }
        
        if (isMissing) {
            val pFinal = _uiState.value.project.copy(
                mediaAssets = _uiState.value.project.mediaAssets.map { 
                    if (it.assetId == assetId) it.copy(mediaAvailabilityStatus = com.example.timeline.media.MediaAvailabilityStatus.MISSING_NEEDS_RELINK, errorMessage = "Media file is missing or unreadable.")
                    else it 
                }
            )
            updateState(pFinal)
            return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pFinal = _uiState.value.project.copy(
                mediaAssets = _uiState.value.project.mediaAssets.map {
                    if (it.assetId == assetId) it.copy(previewStatus = PreviewStatus.NOT_TESTED) else it
                }
            )
            updateState(pFinal, markDirty = false)
            
            val finalAsset = mediaAnalyzer.analyze(asset, targetUriToAnalyze!!)
            val stateNow = _uiState.value
            updateState(stateNow.project.copy(mediaAssets = stateNow.project.mediaAssets.map { if (it.assetId == assetId) finalAsset else it }))
            
            if (finalAsset.proxyStatus == ProxyStatus.REQUIRED || finalAsset.proxyStatus == ProxyStatus.RECOMMENDED) {
                // generateProxyForAsset(finalAsset)
                // Optional to auto-gen here. Let's just track status.
            }
        }
    }
    
    fun generateProxy(assetId: String) {
        val asset = _uiState.value.project.mediaAssets.find { it.assetId == assetId } ?: return
        if (asset.proxyStatus == ProxyStatus.READY || asset.proxyStatus == ProxyStatus.GENERATING) return
        val pFinal = _uiState.value.project.copy(
            mediaAssets = _uiState.value.project.mediaAssets.map {
                if (it.assetId == assetId) it.copy(proxyStatus = ProxyStatus.GENERATING) else it
            }
        )
        updateState(pFinal)
        generateProxyForAsset(asset)
    }
    
    fun cancelProxy(assetId: String) {
        activeProxyJobs[assetId]?.let { workId ->
            workManager.cancelWorkById(workId)
        }
    }
    
    fun addClipFromMedia(mediaAsset: MediaAsset) {
        addClipFromMediaWithMode(mediaAsset, "active")
    }

    fun addClipFromMediaWithMode(mediaAsset: MediaAsset, mode: String) {
        val state = _uiState.value
        
        val selectedClip = state.project.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
        val selectedTrackId = selectedClip?.trackId ?: state.project.tracks.firstOrNull()?.id
        
        val (p2, newClip) = com.example.timeline.engine.edit.AddToTimelineController.createAddClipCommand(state.project, mediaAsset, _playheadFrame.value, mode, selectedTrackId)
        
        // Need to reposition the new track if mode is above or below
        var finalProject = p2
        if ((mode == "above" || mode == "below") && selectedTrackId != null) {
            val tracks = finalProject.tracks.toMutableList()
            val newTrack = tracks.find { it.clips.any { c -> c.id == newClip.id } }
            if (newTrack != null) {
                tracks.remove(newTrack)
                val targetIndex = tracks.indexOfFirst { it.id == selectedTrackId }
                if (targetIndex != -1) {
                    val insertIndex = if (mode == "above") targetIndex else targetIndex + 1
                    tracks.add(insertIndex.coerceIn(0, tracks.size), newTrack)
                    finalProject = finalProject.copy(tracks = tracks)
                } else {
                    tracks.add(newTrack)
                    finalProject = finalProject.copy(tracks = tracks)
                }
            }
        }

        android.util.Log.d("ADD_TO_TIMELINE", "clip created clipId=${newClip.id} assetId=${newClip.mediaId} trackId=${newClip.trackId}")
        val cmd = SnapshotCommand("Add Clip", state.project, finalProject, listOf(newClip.id))
        historyManager.pushCommand(cmd)
        
        _playheadFrame.value = newClip.timelineStart
        _uiState.update { 
            it.copy(
                project = finalProject,
                selectedClipId = newClip.id,
                canUndo = historyManager.canUndo,
                canRedo = historyManager.canRedo
            )
        }
        autosaveManager.markDirty(finalProject)
    }

    private fun updateState(newProject: TimelineProject, markDirty: Boolean = true) {
        _uiState.update { 
            it.copy(
                project = newProject,
                canUndo = historyManager.canUndo,
                canRedo = historyManager.canRedo
            )
        }
        if (markDirty) {
            autosaveManager.markDirty(newProject)
        }
    }
    
    fun setPlayheadFrame(frame: Long) {
        _playheadFrame.value = frame
    }
    
    fun updateClipTransform(clipId: String, transform: com.example.timeline.core.transform.ClipTransform) {
        val state = _uiState.value
        val newProject = state.project.copy(
            tracks = state.project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(transform = transform) else clip
                })
            }
        )
        val cmd = SnapshotCommand("Transform Clip", state.project, newProject, listOf(clipId))
        historyManager.pushCommand(cmd)
        updateState(newProject)
    }
    
    fun updateClipAdjustments(clipId: String, adjustments: com.example.model.adjustments.AdjustmentStack) {
        val state = _uiState.value
        val newProject = state.project.copy(
            tracks = state.project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(adjustments = adjustments) else clip
                })
            }
        )
        val cmd = SnapshotCommand("Adjust Clip", state.project, newProject, listOf(clipId))
        historyManager.pushCommand(cmd)
        updateState(newProject)
    }


    fun selectClip(clipId: String?) {
        _uiState.update { 
            it.copy(
                selectedClipId = clipId,
                selectedClipIds = clipId?.let { setOf(it) } ?: emptySet()
            ) 
        }
    }

    fun updateSelection(clipIds: Set<String>) {
        _uiState.update { 
            it.copy(
                selectedClipId = clipIds.firstOrNull(),
                selectedClipIds = clipIds
            ) 
        }
    }

    fun executeCommand(command: com.example.timeline.core.TimelineCommand) {
        val result = command.execute(_uiState.value.project)
        if (result.isSuccess) {
            historyManager.pushCommand(command)
            updateState(result.project)
        }
    }

    fun zoomIn() {
        setZoom(_uiState.value.pixelsPerFrame * 1.5f)
    }

    fun zoomOut() {
        setZoom(_uiState.value.pixelsPerFrame / 1.5f)
    }

    fun relinkMedia(assetId: String, uri: android.net.Uri) {
        val state = _uiState.value
        val asset = state.project.mediaAssets.find { it.assetId == assetId } ?: return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val copied = copyMediaToLocalStore(uri, assetId, asset.displayName, state.project.id)
            if (copied != null) {
                val updatedAsset = asset.copy(
                    localOriginalUriString = copied,
                    mediaAvailabilityStatus = com.example.timeline.media.MediaAvailabilityStatus.AVAILABLE_LOCAL_COPY
                )
                val updatedAssets = state.project.mediaAssets.map { if (it.assetId == assetId) updatedAsset else it }
                updateState(state.project.copy(mediaAssets = updatedAssets))
                enqueueMediaAnalysis(android.net.Uri.parse(copied))
            }
        }
    }

    fun deleteSelectedClip(ripple: Boolean = false, linkedSelectionEnabled: Boolean = false) {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: return
        
        val controller = LinkedSelectionController()
        val clipsToDelete = if (linkedSelectionEnabled) controller.getLinkedClips(clipId, state.project, true) else setOf(clipId)
        
        // Filter out locked tracks
        val validClipsToDelete = clipsToDelete.filter { cid ->
            val track = state.project.tracks.find { it.clips.any { c -> c.id == cid } }
            track?.isLocked != true
        }
        
        if (validClipsToDelete.isEmpty()) return

        val cmd = if (ripple) {
            CommandFactory.createRippleDeleteCommand(state.project, validClipsToDelete.toList())
        } else {
            SnapshotCommand(
                "Delete Clip",
                state.project,
                com.example.timeline.core.CoreEditEngine.deleteClips(state.project, validClipsToDelete.toList(), rippleMode = false).project,
                validClipsToDelete.toList()
            )
        }
        
        if (cmd != null) {
            val res = cmd.execute(state.project)
            if (res.isSuccess) {
                historyManager.pushCommand(cmd)
                updateState(res.project)
                selectClip(null)
            }
        }
    }
    
    fun addMarkerAtPlayhead() {
        val state = _uiState.value
        val marker = TimelineMarker(frame = _playheadFrame.value, name = "Marker")
        val p2 = CoreMarkerEngine.addProjectMarker(state.project, marker)
        val cmd = SnapshotCommand("Add Marker", state.project, p2, emptyList())
        historyManager.pushCommand(cmd)
        updateState(p2)
    }

    fun createCompoundClip() {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: return
        val trackAndClip = findClipAndTrack(state.project, clipId) ?: return
        val cmd = CommandFactory.createCompoundClipCommand(
            projectBefore = state.project,
            clipIds = listOf(clipId),
            newSequenceName = "Compound Clip",
            destTrackId = trackAndClip.first.id
        )
        if (cmd != null) {
            val res = cmd.execute(state.project)
            if (res.isSuccess) {
                historyManager.pushCommand(cmd)
                updateState(res.project)
                selectClip(null)
            }
        }
    }

    fun splitAtPlayhead() {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: return
        val cmd = CommandFactory.createSplitCommand(state.project, clipId, _playheadFrame.value)
        if (cmd != null) {
            val res = cmd.execute(state.project)
            if (res.isSuccess) {
                historyManager.pushCommand(cmd)
                updateState(res.project)
                selectClip(null)
            }
        }
    }

    fun moveSelectedClip(newStartFrame: Long) {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: return
        
        val clipAndTrack = findClipAndTrack(state.project, clipId) ?: return
        val newClip = clipAndTrack.second.copy(timelineStart = newStartFrame)
        
        val p1 = CoreEditEngine.deleteClips(state.project, listOf(clipId)).project
        val p2 = CoreTimelineEngine.addClip(p1, clipAndTrack.first.id, newClip).project
        
        val cmd = SnapshotCommand("Move Clip", state.project, p2, listOf(clipId))
        historyManager.pushCommand(cmd)
        updateState(p2)
    }

    fun trimClipStart(clipId: String, newStartFrame: Long, linkedSelectionEnabled: Boolean = false) {
        val state = _uiState.value
        val controller = LinkedSelectionController()
        val clipsToTrim = if (linkedSelectionEnabled) controller.getLinkedClips(clipId, state.project, true) else setOf(clipId)
        
        val commands = clipsToTrim.mapNotNull { cid ->
            val track = state.project.tracks.find { it.clips.any { c -> c.id == cid } } ?: return@mapNotNull null
            if (track.isLocked) return@mapNotNull null
            
            val clip = state.project.tracks.flatMap { it.clips }.find { c -> c.id == clipId } ?: return@mapNotNull null
            val delta = newStartFrame - clip.timelineStart
            
            val targetClip = state.project.tracks.flatMap { it.clips }.find { c -> c.id == cid } ?: return@mapNotNull null
            val targetNewStart = targetClip.timelineStart + delta
            
            CommandFactory.createTrimStartCommand(state.project, cid, targetNewStart)
        }
        
        if (commands.isNotEmpty()) {
            val cmd = if (commands.size == 1) commands.first() else com.example.timeline.core.CompositeTimelineCommand("Trim Linked Clips", commands)
            executeCommand(cmd)
        }
    }

    fun trimSelectedClipStart(newStartFrame: Long) {
        val cid = _uiState.value.selectedClipId ?: return
        trimClipStart(cid, newStartFrame)
    }

    fun trimClipEnd(clipId: String, newEndFrame: Long, linkedSelectionEnabled: Boolean = false) {
        val state = _uiState.value
        val controller = LinkedSelectionController()
        val clipsToTrim = if (linkedSelectionEnabled) controller.getLinkedClips(clipId, state.project, true) else setOf(clipId)
        
        val commands = clipsToTrim.mapNotNull { cid ->
            val track = state.project.tracks.find { it.clips.any { c -> c.id == cid } } ?: return@mapNotNull null
            if (track.isLocked) return@mapNotNull null

            val clip = state.project.tracks.flatMap { it.clips }.find { c -> c.id == clipId } ?: return@mapNotNull null
            val delta = newEndFrame - clip.timelineEnd
            
            val targetClip = state.project.tracks.flatMap { it.clips }.find { c -> c.id == cid } ?: return@mapNotNull null
            val targetNewEnd = targetClip.timelineEnd + delta
            
            CommandFactory.createTrimEndCommand(state.project, cid, targetNewEnd)
        }
        
        if (commands.isNotEmpty()) {
            val cmd = if (commands.size == 1) commands.first() else com.example.timeline.core.CompositeTimelineCommand("Trim Linked Clips", commands)
            executeCommand(cmd)
        }
    }

    fun trimSelectedClipEnd(newEndFrame: Long) {
        val cid = _uiState.value.selectedClipId ?: return
        trimClipEnd(cid, newEndFrame)
    }

    fun undo() {
        if (!historyManager.canUndo) return
        val res = historyManager.undo(_uiState.value.project)
        if (res.isSuccess) updateState(res.project)
    }

    fun redo() {
        if (!historyManager.canRedo) return
        val res = historyManager.redo(_uiState.value.project)
        if (res.isSuccess) updateState(res.project)
    }

    fun pushHistoryCommand(cmd: com.example.timeline.core.TimelineCommand) {
        historyManager.pushCommand(cmd)
        updateState(cmd.execute(_uiState.value.project).project)
    }

    fun updateStateWithoutHistory(project: TimelineProject) {
        updateState(project, markDirty = false)
        // Ensure UI stays fluid without triggering history logic unnecessarily.
    }

    fun renameTrack(trackId: String, newName: String) {
        val state = _uiState.value
        val result = com.example.timeline.core.CoreTimelineEngine.renameTrack(state.project, trackId, newName)
        if (result.isSuccess) {
            val cmd = SnapshotCommand("Rename Layer", state.project, result.project, emptyList())
            historyManager.pushCommand(cmd)
            updateState(result.project)
        }
    }

    fun deleteTrack(trackId: String) {
        val state = _uiState.value
        val result = com.example.timeline.core.CoreTimelineEngine.removeTrack(state.project, trackId)
        if (result.isSuccess) {
            val cmd = SnapshotCommand("Delete Layer", state.project, result.project, emptyList())
            historyManager.pushCommand(cmd)
            updateState(result.project)
        }
    }

    fun moveTrack(trackId: String, newIndex: Int) {
        val state = _uiState.value
        val result = com.example.timeline.core.CoreTimelineEngine.reorderTrack(state.project, trackId, newIndex)
        if (result.isSuccess) {
            val cmd = SnapshotCommand("Reorder Layer", state.project, result.project, emptyList())
            historyManager.pushCommand(cmd)
            updateState(result.project)
        }
    }

    fun addTrack(type: com.example.timeline.core.TrackType = com.example.timeline.core.TrackType.VIDEO) {
        val state = _uiState.value
        val newTrack = com.example.timeline.core.TimelineTrack(
            id = java.util.UUID.randomUUID().toString(),
            name = "Track ${state.project.tracks.size + 1}",
            type = type,
            clips = emptyList()
        )
        val result = com.example.timeline.core.CoreTimelineEngine.addTrack(state.project, newTrack)
        if (result.isSuccess) {
            val cmd = SnapshotCommand("Add Layer", state.project, result.project, emptyList())
            historyManager.pushCommand(cmd)
            updateState(result.project)
        }
    }

    fun createNewProject(settings: com.example.timeline.core.ProjectSettings) {
        val newProject = TimelineProject(
            id = java.util.UUID.randomUUID().toString(),
            settings = settings,
            tracks = emptyList(),
            mediaAssets = emptyList()
        )
        _uiState.update { 
            it.copy(
                project = newProject,
                showProjectSetup = false
            )
        }
        autosaveManager.markDirty(newProject)
    }

    fun forceAutosaveProject() {
        autosaveManager.forceAutosave(_uiState.value.project)
    }

    fun forceSaveProject() {
        autosaveManager.markDirty(_uiState.value.project)
    }

    fun toggleDebugPanel() {
        _uiState.update { it.copy(showDebugPanel = !it.showDebugPanel) }
    }

    private fun findClipAndTrack(project: TimelineProject, clipId: String): Pair<TimelineTrack, TimelineClip>? {
        for (track in project.tracks) {
            val clip = track.clips.find { it.id == clipId }
            if (clip != null) return Pair(track, clip)
        }
        return null
    }
    fun setZoom(pixelsPerFrame: Float) { _uiState.update { it.copy(pixelsPerFrame = pixelsPerFrame.coerceIn(0.1f, 50f)) } }

    fun updateSnapSettings(settings: TimelineSnapSettings) {
        _uiState.update { it.copy(snapSettings = settings) }
    }

    fun rippleEdit(clipId: String, deltaFrames: Long, isStart: Boolean = false) {
        val state = _uiState.value
        if (com.example.timeline.engine.native.NativeTimelineCore.isAvailable()) {
            val newProject = com.example.timeline.engine.native.NativeEditAdapter.rippleEdit(state.project, clipId, isStart, deltaFrames)
            val cmd = SnapshotCommand("Ripple Edit", state.project, newProject, listOf(clipId))
            historyManager.pushCommand(cmd)
            updateState(newProject)
        } else {
            val pair = findClipAndTrack(state.project, clipId) ?: return
            if (isStart) trimSelectedClipStart(pair.second.timelineStart + deltaFrames)
            else trimSelectedClipEnd(pair.second.timelineEnd + deltaFrames)
        }
    }

    fun rollEdit(leftClipId: String, rightClipId: String, deltaFrames: Long) {
        val state = _uiState.value
        if (com.example.timeline.engine.native.NativeTimelineCore.isAvailable()) {
            val newProject = com.example.timeline.engine.native.NativeEditAdapter.rollEdit(state.project, leftClipId, rightClipId, deltaFrames)
            val cmd = SnapshotCommand("Roll Edit", state.project, newProject, listOf(leftClipId, rightClipId))
            historyManager.pushCommand(cmd)
            updateState(newProject)
        }
    }

    fun rollEdit(clipId: String, deltaFrames: Long) {
        val state = _uiState.value
        val track = state.project.tracks.find { it.clips.any { c -> c.id == clipId } } ?: return
        val clips = track.clips.sortedBy { it.timelineStart }
        val idx = clips.indexOfFirst { it.id == clipId }
        if (idx > 0) {
            rollEdit(clips[idx - 1].id, clipId, deltaFrames)
        }
    }

    fun slipEdit(clipId: String, deltaFrames: Long) {
        executeCommand(com.example.timeline.core.SlipCommand(clipId, deltaFrames))
    }

    fun slideEdit(clipId: String, deltaFrames: Long) {
        executeCommand(com.example.timeline.core.SlideCommand(clipId, deltaFrames))
    }

    fun nudgeSelectedClip(dir: Int) {
        val cid = _uiState.value.selectedClipId ?: return
        val pair = findClipAndTrack(_uiState.value.project, cid) ?: return
        moveSelectedClip(pair.second.timelineStart + dir)
    }

    fun moveSelectedClipUpTrack() {
        val cid = _uiState.value.selectedClipId ?: return
        val project = _uiState.value.project
        val (track, clip) = findClipAndTrack(project, cid) ?: return
        val trackIndex = project.tracks.indexOf(track)
        if (trackIndex > 0) {
            val res = CoreTimelineEngine.moveClipToTrack(project, cid, track.id, project.tracks[trackIndex - 1].id, clip.timelineStart)
            if (res.error == null) updateState(res.project)
        }
    }

    fun moveSelectedClipDownTrack() {
        val cid = _uiState.value.selectedClipId ?: return
        val project = _uiState.value.project
        val (track, clip) = findClipAndTrack(project, cid) ?: return
        val trackIndex = project.tracks.indexOf(track)
        if (trackIndex < project.tracks.size - 1) {
            val res = CoreTimelineEngine.moveClipToTrack(project, cid, track.id, project.tracks[trackIndex + 1].id, clip.timelineStart)
            if (res.error == null) updateState(res.project)
        }
    }

    fun trimSelectedClipToPlayhead(start: Boolean) {
        val cid = _uiState.value.selectedClipId ?: return
        val project = _uiState.value.project
        val (track, clip) = findClipAndTrack(project, cid) ?: return
        // Simplistic assumption: grab the first playhead if not passed
        val playhead = project.tracks.firstOrNull()?.clips?.firstOrNull()?.timelineStart ?: 0L 
        if (start) {
            trimSelectedClipStart(playhead)
        } else {
            trimSelectedClipEnd(playhead)
        }
    }

    fun extendEditToPlayhead() {
        // Extend selected clip to playhead
        trimSelectedClipToPlayhead(start = false)
    }

    fun lockTrack(id: String, locked: Boolean) {
        updateState(com.example.timeline.core.CoreTimelineEngine.lockTrack(_uiState.value.project, id, locked).project)
    }
    fun enableTrack(id: String, enabled: Boolean) {
        updateState(com.example.timeline.core.CoreTimelineEngine.setTrackEnabled(_uiState.value.project, id, enabled).project)
    }
    fun muteTrack(id: String, muted: Boolean) {
        updateState(com.example.timeline.core.CoreTimelineEngine.setTrackMute(_uiState.value.project, id, muted).project)
    }
    fun soloTrack(id: String, solo: Boolean) {
        updateState(com.example.timeline.core.CoreTimelineEngine.setTrackSolo(_uiState.value.project, id, solo).project)
    }
}
