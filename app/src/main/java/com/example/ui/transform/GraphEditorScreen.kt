package com.example.ui.transform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.timeline.core.transform.TransformInterpolation
import com.example.timeline.core.transform.TransformKeyframe
import com.example.timeline.core.transform.PairFloat
import com.example.timeline.core.transform.TransformKeyframeTrack
import com.example.timeline.ui.TimelineViewModel
import kotlin.math.abs
import kotlin.math.max

enum class GraphParameter {
    POSITION_X, POSITION_Y, SCALE, ROTATION, OPACITY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphEditorScreen(
    timelineViewModel: TimelineViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by timelineViewModel.uiState.collectAsState()
    val clipId = uiState.selectedClipId
    val trackInfo = uiState.project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } }
    val clip = trackInfo?.clips?.find { it.id == clipId }

    if (clip == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a clip to open Graph Editor")
        }
        return
    }

    var selectedParam by remember { mutableStateOf(GraphParameter.ROTATION) }
    var expandedMenu by remember { mutableStateOf(false) }

    val transform = clip.transform
    val currentTrack = when (selectedParam) {
        GraphParameter.POSITION_X -> TransformKeyframeTrack("x", transform.positionTrack.keyframes.map { TransformKeyframe(it.id, "x", it.frame, it.value.first, it.interpolation, it.bezierHandleLeft, it.bezierHandleRight) })
        GraphParameter.POSITION_Y -> TransformKeyframeTrack("y", transform.positionTrack.keyframes.map { TransformKeyframe(it.id, "y", it.frame, it.value.second, it.interpolation, it.bezierHandleLeft, it.bezierHandleRight) })
        GraphParameter.SCALE -> TransformKeyframeTrack("scale", transform.scaleTrack.keyframes.map { TransformKeyframe(it.id, "scale", it.frame, it.value.first, it.interpolation, it.bezierHandleLeft, it.bezierHandleRight) })
        GraphParameter.ROTATION -> transform.rotationTrack
        GraphParameter.OPACITY -> transform.opacityTrack
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Parameter: ", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { expandedMenu = true }) {
                    Text(selectedParam.name)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                }
                DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                    GraphParameter.values().forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                selectedParam = p
                                expandedMenu = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        GraphCurveView(
            track = currentTrack,
            onKeyframeUpdate = { updatedKf ->
                val newTransform = when (selectedParam) {
                    GraphParameter.POSITION_X -> {
                        val original = transform.positionTrack.keyframes.first { it.id == updatedKf.id }
                        transform.copy(positionTrack = transform.positionTrack.updateKeyframe(original.copy(frame = updatedKf.frame, value = PairFloat(updatedKf.value, original.value.second), interpolation = updatedKf.interpolation, bezierHandleLeft = updatedKf.bezierHandleLeft, bezierHandleRight = updatedKf.bezierHandleRight)))
                    }
                    GraphParameter.POSITION_Y -> {
                        val original = transform.positionTrack.keyframes.first { it.id == updatedKf.id }
                        transform.copy(positionTrack = transform.positionTrack.updateKeyframe(original.copy(frame = updatedKf.frame, value = PairFloat(original.value.first, updatedKf.value), interpolation = updatedKf.interpolation, bezierHandleLeft = updatedKf.bezierHandleLeft, bezierHandleRight = updatedKf.bezierHandleRight)))
                    }
                    GraphParameter.SCALE -> {
                        val original = transform.scaleTrack.keyframes.first { it.id == updatedKf.id }
                        transform.copy(scaleTrack = transform.scaleTrack.updateKeyframe(original.copy(frame = updatedKf.frame, value = PairFloat(updatedKf.value, updatedKf.value), interpolation = updatedKf.interpolation, bezierHandleLeft = updatedKf.bezierHandleLeft, bezierHandleRight = updatedKf.bezierHandleRight)))
                    }
                    GraphParameter.ROTATION -> transform.copy(rotationTrack = transform.rotationTrack.updateKeyframe(updatedKf))
                    GraphParameter.OPACITY -> transform.copy(opacityTrack = transform.opacityTrack.updateKeyframe(updatedKf))
                }
                timelineViewModel.updateClipTransform(clip.id, newTransform)
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
fun GraphCurveView(
    track: TransformKeyframeTrack<Float>,
    onKeyframeUpdate: (TransformKeyframe<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (track.keyframes.size < 2) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text("Add at least 2 keyframes to edit curve", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingHandle by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // index to isLeft
    var draggedFrameAccumulator by remember { mutableStateOf<Float?>(null) }
    var draggedValueAccumulator by remember { mutableStateOf<Float?>(null) }
    
    // Zoom and Pan
    var scaleX by remember { mutableStateOf(1f) }
    var scaleY by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    val timelineMin = track.keyframes.minOf { it.frame }
    val timelineMax = track.keyframes.maxOf { it.frame }
    val duration = max(timelineMax - timelineMin, 1L).toFloat()

    val valueMin = track.keyframes.minOf { it.value }
    val valueMax = track.keyframes.maxOf { it.value }
    val valueRange = max(valueMax - valueMin, 0.001f)

    Column(modifier = modifier) {
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        val curveColor = MaterialTheme.colorScheme.primary
        val handleColor = MaterialTheme.colorScheme.secondary
        
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { scaleX *= 1.2f }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Zoom In Time") }
            OutlinedButton(onClick = { scaleX /= 1.2f }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Zoom Out Time") }
            OutlinedButton(onClick = { scaleY *= 1.2f }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Zoom In Value") }
            OutlinedButton(onClick = { scaleY /= 1.2f }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Zoom Out Value") }
            OutlinedButton(onClick = { scaleX = 1f; scaleY = 1f; panX = 0f; panY = 0f }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Reset View") }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(track.keyframes) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        if (draggingIndex == null && draggingHandle == null) {
                            panX += pan.x
                            panY += pan.y
                            // Minimal zoom via pinch if needed, but buttons above handle primary scale.
                        }
                    }
                }
                .pointerInput(track.keyframes) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width
                            val h = size.height
                            val padding = 20f
                            val effectiveW = w - 2 * padding
                            val effectiveH = h - 2 * padding
                            
                            var foundHit = false
                            
                            fun getScreenPos(frame: Long, value: Float): Offset {
                                val nx = ((frame - timelineMin) / duration) * effectiveW * scaleX + panX
                                val ny = effectiveH - ((value - valueMin) / valueRange) * effectiveH * scaleY + panY
                                return Offset(padding + nx, padding + ny)
                            }
                            
                            // Check handles first
                            for (i in track.keyframes.indices) {
                                val kf = track.keyframes[i]
                                if (kf.interpolation == TransformInterpolation.BEZIER) {
                                    val pos = getScreenPos(kf.frame, kf.value)
                                    val kx = pos.x
                                    val ky = pos.y
                                    
                                    if (i > 0) {
                                        val leftHandleOffset = kf.bezierHandleLeft ?: -20f
                                        val hx = kx - 20f * scaleX
                                        val hy = ky - leftHandleOffset * scaleY
                                        if (abs(offset.x - hx) < 30f && abs(offset.y - hy) < 30f) {
                                            draggingHandle = i to true
                                            selectedIndex = i
                                            draggedValueAccumulator = leftHandleOffset
                                            foundHit = true
                                            break
                                        }
                                    }
                                    if (i < track.keyframes.size - 1) {
                                        val rightHandleOffset = kf.bezierHandleRight ?: 20f
                                        val hx = kx + 20f * scaleX
                                        val hy = ky - rightHandleOffset * scaleY
                                        if (abs(offset.x - hx) < 30f && abs(offset.y - hy) < 30f) {
                                            draggingHandle = i to false
                                            selectedIndex = i
                                            draggedValueAccumulator = rightHandleOffset
                                            foundHit = true
                                            break
                                        }
                                    }
                                }
                            }
                            
                            if (!foundHit) {
                                // Check keyframes
                                for (i in track.keyframes.indices) {
                                    val kf = track.keyframes[i]
                                    val pos = getScreenPos(kf.frame, kf.value)
                                    if (abs(offset.x - pos.x) < 30f && abs(offset.y - pos.y) < 30f) {
                                        draggingIndex = i
                                        selectedIndex = i
                                        draggedFrameAccumulator = kf.frame.toFloat()
                                        draggedValueAccumulator = kf.value
                                        break
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            draggingIndex = null
                            draggingHandle = null
                            draggedFrameAccumulator = null
                            draggedValueAccumulator = null
                        },
                        onDragCancel = {
                            draggingIndex = null
                            draggingHandle = null
                            draggedFrameAccumulator = null
                            draggedValueAccumulator = null
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val w = size.width
                        val h = size.height
                        val padding = 20f
                        val effectiveW = w - 2 * padding
                        val effectiveH = h - 2 * padding
                        
                        if (draggingHandle != null && draggedValueAccumulator != null) {
                            val (idx, isLeft) = draggingHandle!!
                            val kf = track.keyframes[idx]
                            val yDeltaInValue = -(dragAmount.y / effectiveH / scaleY) * valueRange
                            draggedValueAccumulator = draggedValueAccumulator!! + yDeltaInValue
                            if (isLeft) {
                                onKeyframeUpdate(kf.copy(bezierHandleLeft = draggedValueAccumulator!!))
                            } else {
                                onKeyframeUpdate(kf.copy(bezierHandleRight = draggedValueAccumulator!!))
                            }
                        } else if (draggingIndex != null && draggedFrameAccumulator != null && draggedValueAccumulator != null) {
                            val idx = draggingIndex!!
                            val kf = track.keyframes[idx]
                            
                            val frameDelta = (dragAmount.x / effectiveW / scaleX) * duration
                            val valueDelta = -(dragAmount.y / effectiveH / scaleY) * valueRange
                            
                            draggedFrameAccumulator = draggedFrameAccumulator!! + frameDelta
                            draggedValueAccumulator = draggedValueAccumulator!! + valueDelta
                            
                            val newFrame = max(0L, Math.round(draggedFrameAccumulator!!).toLong())
                            val newValue = draggedValueAccumulator!!
                            
                            onKeyframeUpdate(kf.copy(frame = newFrame, value = newValue))
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val padding = 20f
            val effectiveW = w - 2 * padding
            val effectiveH = h - 2 * padding
            
            fun getScreenPos(frame: Long, value: Float): Offset {
                val nx = ((frame - timelineMin) / duration) * effectiveW * scaleX + panX
                val ny = effectiveH - ((value - valueMin) / valueRange) * effectiveH * scaleY + panY
                return Offset(padding + nx, padding + ny)
            }
            
            // Grid
            for (i in 1..4) {
                drawLine(gridColor, Offset(0f, padding + effectiveH * i / 5), Offset(w, padding + effectiveH * i / 5))
                drawLine(gridColor, Offset(padding + effectiveW * i / 5, 0f), Offset(padding + effectiveW * i / 5, h))
            }

            val path = Path()
            
            for (i in track.keyframes.indices) {
                val kf = track.keyframes[i]
                val pos = getScreenPos(kf.frame, kf.value)
                val x = pos.x
                val y = pos.y
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevKf = track.keyframes[i - 1]
                    val prevPos = getScreenPos(prevKf.frame, prevKf.value)
                    val prevX = prevPos.x
                    val prevY = prevPos.y
                    
                    if (prevKf.interpolation == TransformInterpolation.HOLD) {
                        path.lineTo(x, prevY)
                        path.lineTo(x, y)
                    } else if (prevKf.interpolation == TransformInterpolation.BEZIER) {
                        val handleRightY = prevY - (prevKf.bezierHandleRight ?: 20f) * effectiveH / valueRange * scaleY
                        val handleLeftY = y - (kf.bezierHandleLeft ?: -20f) * effectiveH / valueRange * scaleY
                        val cx1 = prevX + (x - prevX) / 3f
                        val cx2 = prevX + 2f * (x - prevX) / 3f
                        path.cubicTo(cx1, handleRightY, cx2, handleLeftY, x, y)
                    } else if (prevKf.interpolation == TransformInterpolation.EASE_IN_OUT) {
                        val cx1 = prevX + (x - prevX) * 0.5f
                        val cx2 = prevX + (x - prevX) * 0.5f
                        path.cubicTo(cx1, prevY, cx2, y, x, y)
                    } else if (prevKf.interpolation == TransformInterpolation.EASE_IN) {
                        val cx1 = prevX + (x - prevX) * 0.5f
                        path.quadraticBezierTo(cx1, prevY, x, y)
                    } else if (prevKf.interpolation == TransformInterpolation.EASE_OUT) {
                        val cx1 = prevX + (x - prevX) * 0.5f
                        path.quadraticBezierTo(cx1, y, x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
            }
            
            drawPath(path, curveColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            
            // Draw Points & Handles
            for (i in track.keyframes.indices) {
                val kf = track.keyframes[i]
                val pos = getScreenPos(kf.frame, kf.value)
                val kx = pos.x
                val ky = pos.y
                
                if (kf.interpolation == TransformInterpolation.BEZIER && i == selectedIndex) {
                    if (i > 0) {
                        val hY = ky - (kf.bezierHandleLeft ?: -20f) * effectiveH / valueRange * scaleY
                        val hX = kx - 20f * scaleX
                        drawLine(handleColor, Offset(kx, ky), Offset(hX, hY), strokeWidth = 1.dp.toPx())
                        drawCircle(Color.White, 3.dp.toPx(), center = Offset(hX, hY))
                        drawCircle(handleColor, 2.dp.toPx(), center = Offset(hX, hY))
                    }
                    if (i < track.keyframes.size - 1) {
                        val hY = ky - (kf.bezierHandleRight ?: 20f) * effectiveH / valueRange * scaleY
                        val hX = kx + 20f * scaleX
                        drawLine(handleColor, Offset(kx, ky), Offset(hX, hY), strokeWidth = 1.dp.toPx())
                        drawCircle(Color.White, 3.dp.toPx(), center = Offset(hX, hY))
                        drawCircle(handleColor, 2.dp.toPx(), center = Offset(hX, hY))
                    }
                }
                
                drawCircle(if (i == selectedIndex) Color.White else curveColor, 5.dp.toPx(), center = Offset(kx, ky))
                if (i == selectedIndex) {
                    drawCircle(curveColor, 3.dp.toPx(), center = Offset(kx, ky))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedIndex != null) {
            val kf = track.keyframes[selectedIndex!!]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Keyframe Interpolation: ", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                TransformInterpolation.values().forEach { mode ->
                    FilterChip(
                        selected = kf.interpolation == mode,
                        onClick = { onKeyframeUpdate(kf.copy(interpolation = mode)) },
                        label = { Text(mode.name, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (kf.interpolation == TransformInterpolation.BEZIER) {
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(onClick = { onKeyframeUpdate(kf.copy(bezierHandleLeft = null, bezierHandleRight = null)) }) {
                        Text("Reset Handles")
                    }
                }
            }
        }
    }
}
