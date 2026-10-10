package com.example.timeline.ui
import androidx.compose.material3.Text

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.example.timeline.engine.native.NativeTimelineCore
import com.example.timeline.engine.native.NativeTimelineMetrics
import com.example.timeline.core.TrackType
import kotlin.math.max
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add


@Composable
fun CineTimelineCanvas(
    onImportClick: () -> Unit = {},
    uiState: TimelineUiState,
    playheadFrame: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClipSelected: (String?) -> Unit,
    onPlayheadMoved: (Long) -> Unit,
    onClipMoved: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onZoomChange: ((Float) -> Unit)? = null,
    onSplit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRenameTrack: (String, String) -> Unit = { _, _ -> },
    onDeleteTrack: (String) -> Unit = {},
    onMoveTrack: (String, Int) -> Unit = { _, _ -> }
) {
    var scrollX by remember { mutableStateOf(0f) }
    var scrollY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var dragAction by remember { mutableStateOf<TimelineDragAction>(TimelineDragAction.None) }
    var dragGhostStartFrame by remember { mutableStateOf<Long?>(null) }
    var dragGhostEndFrame by remember { mutableStateOf<Long?>(null) }
    var trackToRename by remember { mutableStateOf<com.example.timeline.core.TimelineTrack?>(null) }
    var renameText by remember { mutableStateOf("") }
    
    val pixelsPerFrame = uiState.pixelsPerFrame
    val trackHeight = 80f
    val trackPadding = 12f
    
    // Transformable state for pinch zoom and pan
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (onZoomChange != null) {
            onZoomChange(pixelsPerFrame * zoomChange)
        }
        scrollX = max(0f, scrollX - panChange.x)
        scrollY = max(0f, scrollY - panChange.y)
    }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .transformable(transformableState)
    ) {
        val viewportWidth = constraints.maxWidth
        val centerX = viewportWidth / 2f
        
        val isFollowing by FollowPlayheadController.isFollowing.collectAsState()
        // Auto-center playhead if needed (only horizontal)
        LaunchedEffect(playheadFrame) {
            if (!isDragging && !transformableState.isTransformInProgress && isPlaying && isFollowing) {
                 scrollX = max(0f, NativeTimelineCore.frameToX(playheadFrame, pixelsPerFrame) - centerX)
            }
        }
        
        Row(modifier = Modifier.fillMaxSize()) {
            // Track Headers
            if (uiState.project.tracks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1E1E1E))
                        .padding(top = 40.dp)
                        .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { (-scrollY).toDp() })
                ) {
                    uiState.project.tracks.forEachIndexed { index, track ->
                        LayerHeaderView(
                            track = track,
                            trackIndex = index,
                            onRename = { trackToRename = track; renameText = track.name },
                            onDelete = { onDeleteTrack(track.id) },
                            onMoveUp = { onMoveTrack(track.id, index - 1) },
                            onMoveDown = { onMoveTrack(track.id, index + 1) },
                            modifier = Modifier
                                .height(with(androidx.compose.ui.platform.LocalDensity.current) { trackHeight.toDp() })
                                .padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { trackPadding.toDp() })
                        )
                    }
                }
            }
            
            // Timeline Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    
                    .pointerInput(pixelsPerFrame, scrollX, scrollY, uiState.selectedClipId) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val tapX = offset.x + scrollX
                                val tapY = offset.y + scrollY - 40.dp.toPx()
                                val hitClip = TimelineGestureController.hitTest(tapX, tapY, uiState.project, pixelsPerFrame)
                                if (hitClip != null) {
                                    onClipSelected(hitClip.id)
                                    contextMenuOffset = IntOffset(offset.x.toInt(), offset.y.toInt())
                                    contextMenuVisible = true
                                }
                            },
                            onTap = { offset ->
                                contextMenuVisible = false
                                val tapX = offset.x + scrollX
                                val tapY = offset.y + scrollY - 40.dp.toPx()
                                val hitClip = TimelineGestureController.hitTest(tapX, tapY, uiState.project, pixelsPerFrame)
                                onClipSelected(hitClip?.id)
                                
                                if (hitClip == null) {
                                    val frame = NativeTimelineCore.xToFrame(tapX, pixelsPerFrame)
                                    onPlayheadMoved(max(0, frame))
                                }
                            }
                        )
                    }
                    .pointerInput(pixelsPerFrame, scrollX, scrollY, uiState.selectedClipId) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                contextMenuVisible = false
                                dragAction = TimelineGestureController.determineDragAction(
                                    startOffset = offset,
                                    scrollX = scrollX,
                                    scrollY = scrollY,
                                    rulerHeightPx = 40.dp.toPx(),
                                    project = uiState.project,
                                    pixelsPerFrame = pixelsPerFrame,
                                    selectedClipId = uiState.selectedClipId
                                )
                                when (val action = dragAction) {
                                    is TimelineDragAction.MoveClip -> dragGhostStartFrame = action.clip.timelineStart
                                    is TimelineDragAction.TrimStart -> dragGhostStartFrame = action.clip.timelineStart
                                    is TimelineDragAction.TrimEnd -> dragGhostEndFrame = action.clip.timelineEnd
                                    else -> {}
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                when (val action = dragAction) {
                                    is TimelineDragAction.Pan -> {
                                        scrollX = max(0f, scrollX - dragAmount.x)
                                        scrollY = max(0f, scrollY - dragAmount.y)
                                    }
                                    is TimelineDragAction.ScrubPlayhead -> {
                                        val frame = NativeTimelineCore.xToFrame(change.position.x + scrollX, pixelsPerFrame)
                                        onPlayheadMoved(max(0, frame))
                                    }
                                    is TimelineDragAction.MoveClip -> {
                                        val currentX = change.position.x + scrollX
                                        val frame = NativeTimelineCore.xToFrame(currentX, pixelsPerFrame)
                                        val rawStart = max(0L, frame - action.startOffsetFrames)
                                        dragGhostStartFrame = TimelineGestureController.snapFrame(rawStart, uiState.project, playheadFrame, pixelsPerFrame, action.clip.id)
                                    }
                                    is TimelineDragAction.TrimStart -> {
                                        val currentX = change.position.x + scrollX
                                        val frame = NativeTimelineCore.xToFrame(currentX, pixelsPerFrame)
                                        val snapped = TimelineGestureController.snapFrame(max(0L, frame), uiState.project, playheadFrame, pixelsPerFrame, action.clip.id)
                                        // prevent trimming past end
                                        dragGhostStartFrame = minOf(snapped, action.clip.timelineEnd - 1)
                                    }
                                    is TimelineDragAction.TrimEnd -> {
                                        val currentX = change.position.x + scrollX
                                        val frame = NativeTimelineCore.xToFrame(currentX, pixelsPerFrame)
                                        val snapped = TimelineGestureController.snapFrame(max(0L, frame), uiState.project, playheadFrame, pixelsPerFrame, action.clip.id)
                                        // prevent trimming past start
                                        dragGhostEndFrame = maxOf(snapped, action.clip.timelineStart + 1)
                                    }
                                    TimelineDragAction.None -> {}
                                }
                            },
                            onDragEnd = { 
                                isDragging = false
                                when (val action = dragAction) {
                                    is TimelineDragAction.MoveClip -> {
                                        dragGhostStartFrame?.let { onClipMoved(action.clip.id, it) }
                                    }
                                    is TimelineDragAction.TrimStart -> {
                                        dragGhostStartFrame?.let { onTrimStart(action.clip.id, it) }
                                    }
                                    is TimelineDragAction.TrimEnd -> {
                                        dragGhostEndFrame?.let { onTrimEnd(action.clip.id, it) }
                                    }
                                    else -> {}
                                }
                                dragAction = TimelineDragAction.None
                                dragGhostStartFrame = null
                                dragGhostEndFrame = null
                            },
                            onDragCancel = { 
                                isDragging = false
                                dragAction = TimelineDragAction.None
                                dragGhostStartFrame = null
                                dragGhostEndFrame = null
                            }
                        )
                    }) {
                // Scrollable container for tracks
                Box(modifier = Modifier.fillMaxSize().offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { (-scrollY).toDp() })) {
                    // Track backgrounds
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
                        uiState.project.tracks.forEach {
                            Box(modifier = Modifier.fillMaxWidth().height(with(androidx.compose.ui.platform.LocalDensity.current) { trackHeight.toDp() }).background(Color(0xFF141414)))
                            Spacer(modifier = Modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { trackPadding.toDp() }))
                        }
                    }
                    
                    // Layout Clips
                    Layout(
                        content = {
                            val startTime = System.currentTimeMillis()
                            uiState.project.tracks.forEach { track ->
                                track.clips.forEach { clip ->
                                    val isMoving = dragAction is TimelineDragAction.MoveClip && clip.id == (dragAction as TimelineDragAction.MoveClip).clip.id
                                    val isTrimmingStart = dragAction is TimelineDragAction.TrimStart && clip.id == (dragAction as TimelineDragAction.TrimStart).clip.id
                                    val isTrimmingEnd = dragAction is TimelineDragAction.TrimEnd && clip.id == (dragAction as TimelineDragAction.TrimEnd).clip.id
                                    
                                    val effectiveStart = if (isMoving || isTrimmingStart) dragGhostStartFrame ?: clip.timelineStart else clip.timelineStart
                                    val effectiveEnd = if (isTrimmingEnd) dragGhostEndFrame ?: clip.timelineEnd
                                                      else if (isMoving) effectiveStart + (clip.timelineEnd - clip.timelineStart)
                                                      else clip.timelineEnd
                                    
                                    val startDiff = effectiveStart - clip.timelineStart
                                    val endDiff = effectiveEnd - clip.timelineEnd
                                    val effectiveSourceIn = clip.sourceIn + if (isTrimmingStart) startDiff else 0L
                                    val effectiveSourceOut = clip.sourceOut + if (isTrimmingEnd) endDiff else 0L
                                    val effectiveClip = clip.copy(timelineStart = effectiveStart, sourceIn = effectiveSourceIn, sourceOut = effectiveSourceOut)
                                    val mediaAsset = uiState.project.mediaAssets.find { it.assetId == clip.mediaId }
                                    
                                    FilmStripClipView(
                                        clip = effectiveClip,
                                        mediaAsset = mediaAsset,
                                        isSelected = uiState.selectedClipId == clip.id,
                                        pixelsPerFrame = pixelsPerFrame,
                                        scrollX = scrollX,
                                        viewportWidth = viewportWidth.toFloat(),
                                        modifier = Modifier
                                    )
                                }
                            }
                            NativeTimelineMetrics.reportLayoutTime(System.currentTimeMillis() - startTime)
                        }
                    ) { measurables, constraints ->
                        val placeables = measurables.map { it.measure(constraints) }
                        
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            var mIndex = 0
                            
                            if (NativeTimelineCore.isAvailable()) {
                                val inputData = mutableListOf<Long>()
                                uiState.project.tracks.forEachIndexed { trackIndex, track ->
                                    track.clips.forEach { clip ->
                                        val isMoving = dragAction is TimelineDragAction.MoveClip && clip.id == (dragAction as TimelineDragAction.MoveClip).clip.id
                                        val isTrimmingStart = dragAction is TimelineDragAction.TrimStart && clip.id == (dragAction as TimelineDragAction.TrimStart).clip.id
                                        val isTrimmingEnd = dragAction is TimelineDragAction.TrimEnd && clip.id == (dragAction as TimelineDragAction.TrimEnd).clip.id
                                        
                                        val effectiveStart = if (isMoving || isTrimmingStart) dragGhostStartFrame ?: clip.timelineStart else clip.timelineStart
                                        val effectiveEnd = if (isTrimmingEnd) dragGhostEndFrame ?: clip.timelineEnd
                                                          else if (isMoving) effectiveStart + (clip.timelineEnd - clip.timelineStart)
                                                          else clip.timelineEnd
                                        
                                        inputData.addAll(listOf(trackIndex.toLong(), effectiveStart, (effectiveEnd - effectiveStart)))
                                    }
                                }
                                
                                val startNs = System.nanoTime()
                                val layoutData = NativeTimelineCore.nativeLayoutClips(
                                    inputData.toLongArray(),
                                    trackHeight, trackPadding, 40.dp.toPx(),
                                    pixelsPerFrame, scrollX
                                )
                                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                                NativeTimelineMetrics.reportLayoutTime(elapsedMs)
                                
                                var i = 0
                                uiState.project.tracks.forEachIndexed { trackIndex, track ->
                                    track.clips.forEach { clip ->
                                        if (mIndex < placeables.size && i + 4 < layoutData.size) {
                                            val left = layoutData[i + 1].toInt()
                                            val top = layoutData[i + 2].toInt()
                                            placeables[mIndex].placeRelative(x = left, y = top)
                                            mIndex++
                                        }
                                        i += 5
                                    }
                                }
                            } else {
                                uiState.project.tracks.forEachIndexed { trackIndex, track ->
                                    track.clips.forEach { clip ->
                                        if (mIndex < placeables.size) {
                                            val isMoving = dragAction is TimelineDragAction.MoveClip && clip.id == (dragAction as TimelineDragAction.MoveClip).clip.id
                                            val isTrimmingStart = dragAction is TimelineDragAction.TrimStart && clip.id == (dragAction as TimelineDragAction.TrimStart).clip.id
                                            val effectiveStart = if (isMoving || isTrimmingStart) dragGhostStartFrame ?: clip.timelineStart else clip.timelineStart
                                            
                                            val left = (NativeTimelineCore.frameToX(effectiveStart, pixelsPerFrame) - scrollX).toInt()
                                            val top = (trackIndex * (trackHeight + trackPadding) + 40.dp.toPx()).toInt()
                                            placeables[mIndex].placeRelative(x = left, y = top)
                                            mIndex++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                
                Box(modifier = Modifier.offset { contextMenuOffset }) {
                    TimelineContextMenu(
                        expanded = contextMenuVisible,
                        onDismissRequest = { contextMenuVisible = false },
                        onSplit = onSplit,
                        onDelete = onDelete
                    )
                }
                
                // Fixed top overlay for Ruler
                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    TimelineRulerView(scrollX, viewportWidth, pixelsPerFrame, uiState.project.settings.getFpsRational())
                }
                
                // Playhead (also fixed Y relative to scrolling)
                val playheadX = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (NativeTimelineCore.frameToX(playheadFrame, pixelsPerFrame) - scrollX).toDp()
                }
                TimelinePlayheadView(
                    modifier = Modifier.offset(x = playheadX, y = 40.dp)
                )
            }
        }
    }
    
    trackToRename?.let { track ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { trackToRename = null },
            title = { Text("Rename Layer") },
            text = {
                androidx.compose.material3.TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onRenameTrack(track.id, renameText)
                    trackToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { trackToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
