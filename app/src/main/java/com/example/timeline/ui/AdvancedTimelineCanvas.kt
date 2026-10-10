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
import com.example.timeline.engine.native.advanced.NativeAdvancedTimelineCore
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
fun AdvancedTimelineCanvas(
    uiState: TimelineUiState,
    playheadFrame: Long,
    isPlaying: Boolean,
    activeTool: TimelineTool,
    snapEnabled: Boolean,
    linkedSelection: Boolean,
    onClipSelect: (String?) -> Unit,
    onClipMoved: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onRippleEdit: (String, Long, Boolean) -> Unit,
    onRollEdit: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    onSlipEdit: (String, Long) -> Unit,
    onSlideEdit: (String, Long) -> Unit,
    onTrackLock: (String, Boolean) -> Unit,
    onTrackEnable: (String, Boolean) -> Unit,
    onTrackMute: (String, Boolean) -> Unit = { _, _ -> },
    onTrackSolo: (String, Boolean) -> Unit = { _, _ -> },
    onRenameTrack: (String, String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onMoveTrack: (String, Int) -> Unit,
    onPlayheadMoved: (Long) -> Unit,
    onBladeCut: (String, Long) -> Unit = { _, _ -> },
    onSplit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onTrimStartToPlayhead: () -> Unit = {},
    onTrimEndToPlayhead: () -> Unit = {},
    modifier: Modifier = Modifier
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
                 scrollX = max(0f, NativeAdvancedTimelineCore.frameToX(playheadFrame, pixelsPerFrame) - centerX)
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
                        AdvancedTimelineTrackHeader(
                            track = track,
                            trackIndex = index,
                            onRename = { trackToRename = track; renameText = track.name },
                            onDelete = { onDeleteTrack(track.id) },
                            onMoveUp = { onMoveTrack(track.id, index - 1) },
                            onMoveDown = { onMoveTrack(track.id, index + 1) },
                            onToggleLock = { onTrackLock(track.id, track.isLocked) },
                            onToggleVisibility = { onTrackEnable(track.id, track.isVisible) },
                            onToggleMute = { onTrackMute(track.id, track.isMuted) },
                            onToggleSolo = { onTrackSolo(track.id, track.isSolo) },
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
                                val hitClip = AdvancedTimelineGestureController.hitTest(tapX, tapY, uiState.project, pixelsPerFrame)
                                if (hitClip != null) {
                                    onClipSelect(hitClip.id)
                                    contextMenuOffset = IntOffset(offset.x.toInt(), offset.y.toInt())
                                    contextMenuVisible = true
                                }
                            },
                            onTap = { offset ->
                                contextMenuVisible = false
                                val tapX = offset.x + scrollX
                                val tapY = offset.y + scrollY - 40.dp.toPx()
                                val hitClip = AdvancedTimelineGestureController.hitTest(tapX, tapY, uiState.project, pixelsPerFrame)
                                val frame = NativeAdvancedTimelineCore.xToFrame(tapX, pixelsPerFrame)
                                if (activeTool == TimelineTool.BLADE) {
                                    if (hitClip != null) {
                                        onBladeCut(hitClip.id, max(0, frame))
                                    }
                                } else {
                                    onClipSelect(hitClip?.id)
                                    if (hitClip == null) {
                                        onPlayheadMoved(max(0, frame))
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(pixelsPerFrame, scrollX, scrollY, uiState.selectedClipId) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                contextMenuVisible = false
                                dragAction = AdvancedTimelineGestureController.determineDragAction(
                                    startOffset = offset,
                                    scrollX = scrollX,
                                    scrollY = scrollY,
                                    rulerHeightPx = 40.dp.toPx(),
                                    project = uiState.project,
                                    pixelsPerFrame = pixelsPerFrame,
                                    selectedClipIds = uiState.selectedClipIds
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
                                        val frame = NativeAdvancedTimelineCore.xToFrame(change.position.x + scrollX, pixelsPerFrame)
                                        onPlayheadMoved(max(0, frame))
                                    }
                                    is TimelineDragAction.MoveClip -> {
                                        val currentX = change.position.x + scrollX
                                        val frame = NativeAdvancedTimelineCore.xToFrame(currentX, pixelsPerFrame)
                                        val rawStart = max(0L, frame - action.startOffsetFrames)
                                        dragGhostStartFrame = if (snapEnabled) AdvancedTimelineGestureController.snapFrame(rawStart, uiState.project, playheadFrame, pixelsPerFrame, action.clip.id) else rawStart
                                    }
                                    is TimelineDragAction.TrimStart -> {
                                        val currentX = change.position.x + scrollX
                                        val frame = NativeAdvancedTimelineCore.xToFrame(currentX, pixelsPerFrame)
                                        val snapped = if (snapEnabled) AdvancedTimelineGestureController.snapFrame(max(0L, frame), uiState.project, playheadFrame, pixelsPerFrame, action.clip.id) else max(0L, frame)
                                        // prevent trimming past end
                                        dragGhostStartFrame = minOf(snapped, action.clip.timelineEnd - 1)
                                    }
                                    is TimelineDragAction.TrimEnd -> {
                                        val currentX = change.position.x + scrollX
                                        val frame = NativeAdvancedTimelineCore.xToFrame(currentX, pixelsPerFrame)
                                        val snapped = if (snapEnabled) AdvancedTimelineGestureController.snapFrame(max(0L, frame), uiState.project, playheadFrame, pixelsPerFrame, action.clip.id) else max(0L, frame)
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
                                        dragGhostStartFrame?.let { frame ->
                                            val diff = frame - action.clip.timelineStart
                                            when (activeTool) {
                                                TimelineTool.SLIP -> onSlipEdit(action.clip.id, diff)
                                                TimelineTool.SLIDE -> onSlideEdit(action.clip.id, diff)
                                                else -> onClipMoved(action.clip.id, frame)
                                            }
                                        }
                                    }
                                    is TimelineDragAction.TrimStart -> {
                                        dragGhostStartFrame?.let { frame ->
                                            val diff = frame - action.clip.timelineStart
                                            when (activeTool) {
                                                TimelineTool.RIPPLE -> onRippleEdit(action.clip.id, frame, true)
                                                TimelineTool.ROLL -> onRollEdit(action.clip.id, diff, true)
                                                TimelineTool.SLIP -> onSlipEdit(action.clip.id, diff)
                                                TimelineTool.SLIDE -> onSlideEdit(action.clip.id, diff)
                                                else -> onTrimStart(action.clip.id, frame)
                                            }
                                        }
                                    }
                                    is TimelineDragAction.TrimEnd -> {
                                        dragGhostEndFrame?.let { frame ->
                                            val diff = frame - action.clip.timelineEnd
                                            when (activeTool) {
                                                TimelineTool.RIPPLE -> onRippleEdit(action.clip.id, frame, false)
                                                TimelineTool.ROLL -> onRollEdit(action.clip.id, diff, false)
                                                TimelineTool.SLIP -> onSlipEdit(action.clip.id, diff)
                                                TimelineTool.SLIDE -> onSlideEdit(action.clip.id, diff)
                                                else -> onTrimEnd(action.clip.id, frame)
                                            }
                                        }
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
                                    val isMoving = dragAction is TimelineDragAction.MoveClip && clip.id == (dragAction as TimelineDragAction.MoveClip).clip.id && activeTool != TimelineTool.SLIP
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
                                    
                                    AdvancedTimelineClipView(
                                        clip = effectiveClip,
                                        mediaAsset = mediaAsset,
                                        isSelected = uiState.selectedClipIds.contains(clip.id),
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
                            
                            if (NativeAdvancedTimelineCore.isAvailable()) {
                                val inputData = mutableListOf<Long>()
                                uiState.project.tracks.forEachIndexed { trackIndex, track ->
                                    track.clips.forEach { clip ->
                                        val isMoving = dragAction is TimelineDragAction.MoveClip && clip.id == (dragAction as TimelineDragAction.MoveClip).clip.id && activeTool != TimelineTool.SLIP
                                        val isTrimmingStart = dragAction is TimelineDragAction.TrimStart && clip.id == (dragAction as TimelineDragAction.TrimStart).clip.id
                                        val isTrimmingEnd = dragAction is TimelineDragAction.TrimEnd && clip.id == (dragAction as TimelineDragAction.TrimEnd).clip.id
                                        
                                        val effectiveStart = if (isMoving || isTrimmingStart) dragGhostStartFrame ?: clip.timelineStart else clip.timelineStart
                                        val effectiveEnd = if (isTrimmingEnd) dragGhostEndFrame ?: clip.timelineEnd
                                                          else if (isMoving) effectiveStart + (clip.timelineEnd - clip.timelineStart)
                                                          else clip.timelineEnd
                                        
                                        inputData.addAll(listOf(clip.id.hashCode().toLong(), trackIndex.toLong(), effectiveStart, (effectiveEnd - effectiveStart)))
                                    }
                                }
                                
                                val startNs = System.nanoTime()
                                val layoutData = NativeAdvancedTimelineCore.nativeLayoutClipRects(
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
                                            val isMoving = dragAction is TimelineDragAction.MoveClip && clip.id == (dragAction as TimelineDragAction.MoveClip).clip.id && activeTool != TimelineTool.SLIP
                                            val isTrimmingStart = dragAction is TimelineDragAction.TrimStart && clip.id == (dragAction as TimelineDragAction.TrimStart).clip.id
                                            val effectiveStart = if (isMoving || isTrimmingStart) dragGhostStartFrame ?: clip.timelineStart else clip.timelineStart
                                            
                                            val left = (NativeAdvancedTimelineCore.frameToX(effectiveStart, pixelsPerFrame) - scrollX).toInt()
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
                        onDelete = onDelete,
                        onTrimStartToPlayhead = onTrimStartToPlayhead,
                        onTrimEndToPlayhead = onTrimEndToPlayhead
                    )
                }
                
                // Fixed top overlay for Ruler
                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    AdvancedTimelineRuler(scrollX, viewportWidth, pixelsPerFrame, uiState.project.settings.getFpsRational())
                }
                
                // Playhead (also fixed Y relative to scrolling)
                val playheadX = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (NativeAdvancedTimelineCore.frameToX(playheadFrame, pixelsPerFrame) - scrollX).toDp()
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
