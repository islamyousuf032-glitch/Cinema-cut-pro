package com.example.ui.transform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.ui.TimelineViewModel

@Composable
fun TransformPanel(timelineViewModel: TimelineViewModel) {
    val state by timelineViewModel.uiState.collectAsState()
    val clipId = state.selectedClipId
    
    if (clipId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Select a clip to edit transform properties", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    
    val track = state.project.tracks.firstOrNull { it.clips.any { c -> c.id == clipId } }
    val clip = track?.clips?.find { it.id == clipId } ?: return

    val transform = clip.transform
    val currentFrame by timelineViewModel.playheadFrame.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Transform", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val prevFrame = clip.transform.let { t -> 
                            (t.positionTrack.keyframes.map { it.frame } + t.scaleTrack.keyframes.map { it.frame } + t.rotationTrack.keyframes.map { it.frame } + t.opacityTrack.keyframes.map { it.frame })
                        }.distinct().filter { it < currentFrame }.maxOrNull()
                        if (prevFrame != null) {
                            timelineViewModel.setPlayheadFrame(prevFrame)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("< Prev KF", style = MaterialTheme.typography.labelSmall)
                }
                
                OutlinedButton(
                    onClick = {
                        val nextFrame = clip.transform.let { t -> 
                            (t.positionTrack.keyframes.map { it.frame } + t.scaleTrack.keyframes.map { it.frame } + t.rotationTrack.keyframes.map { it.frame } + t.opacityTrack.keyframes.map { it.frame })
                        }.distinct().filter { it > currentFrame }.minOrNull()
                        if (nextFrame != null) {
                            timelineViewModel.setPlayheadFrame(nextFrame)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Next KF >", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        
        // Find if current frame has keyframe and its interpolation
        val currentKfs = mutableListOf<com.example.timeline.core.transform.TransformKeyframe<*>>()
        clip.transform.positionTrack.keyframes.find { it.frame == currentFrame }?.let { currentKfs.add(it) }
        clip.transform.scaleTrack.keyframes.find { it.frame == currentFrame }?.let { currentKfs.add(it) }
        clip.transform.rotationTrack.keyframes.find { it.frame == currentFrame }?.let { currentKfs.add(it) }
        clip.transform.opacityTrack.keyframes.find { it.frame == currentFrame }?.let { currentKfs.add(it) }
        
        if (currentKfs.isNotEmpty()) {
            val commonInterp = currentKfs.first().interpolation
            var expandedInterp by remember { mutableStateOf(false) }
            
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Interpolation (Current KF): ", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { expandedInterp = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(commonInterp.name, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = expandedInterp, onDismissRequest = { expandedInterp = false }) {
                        com.example.timeline.core.transform.TransformInterpolation.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    expandedInterp = false
                                    var newTransform = transform
                                    if (newTransform.positionTrack.keyframes.any { it.frame == currentFrame }) {
                                        newTransform = newTransform.copy(positionTrack = newTransform.positionTrack.updateKeyframe(newTransform.positionTrack.keyframes.first { it.frame == currentFrame }.copy(interpolation = mode)))
                                    }
                                    if (newTransform.scaleTrack.keyframes.any { it.frame == currentFrame }) {
                                        newTransform = newTransform.copy(scaleTrack = newTransform.scaleTrack.updateKeyframe(newTransform.scaleTrack.keyframes.first { it.frame == currentFrame }.copy(interpolation = mode)))
                                    }
                                    if (newTransform.rotationTrack.keyframes.any { it.frame == currentFrame }) {
                                        newTransform = newTransform.copy(rotationTrack = newTransform.rotationTrack.updateKeyframe(newTransform.rotationTrack.keyframes.first { it.frame == currentFrame }.copy(interpolation = mode)))
                                    }
                                    if (newTransform.opacityTrack.keyframes.any { it.frame == currentFrame }) {
                                        newTransform = newTransform.copy(opacityTrack = newTransform.opacityTrack.updateKeyframe(newTransform.opacityTrack.keyframes.first { it.frame == currentFrame }.copy(interpolation = mode)))
                                    }
                                    timelineViewModel.updateClipTransform(clipId, newTransform)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        TransformControlRow(
            label = "Position X",
            value = transform.positionX,
            onValueChange = { 
                val t = if (transform.positionTrack.hasKeyframes()) transform.addPositionKeyframe(currentFrame, it, transform.positionY) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(positionX = it)))
            },
            valueRange = -2000f..2000f,
            isKeyframed = transform.positionTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.positionTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.positionTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(positionTrack = transform.positionTrack.copy(keyframes = transform.positionTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addPositionKeyframe(currentFrame, transform.positionX, transform.positionY))
                }
            }
        )

        TransformControlRow(
            label = "Position Y",
            value = transform.positionY,
            onValueChange = { 
                val t = if (transform.positionTrack.hasKeyframes()) transform.addPositionKeyframe(currentFrame, transform.positionX, it) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(positionY = it)))
            },
            valueRange = -2000f..2000f,
            isKeyframed = transform.positionTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.positionTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.positionTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(positionTrack = transform.positionTrack.copy(keyframes = transform.positionTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addPositionKeyframe(currentFrame, transform.positionX, transform.positionY))
                }
            }
        )

        var uniformScale by remember { mutableStateOf(true) }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = uniformScale, onCheckedChange = { uniformScale = it })
            Text("Uniform Scale", style = MaterialTheme.typography.labelMedium)
        }

        TransformControlRow(
            label = "Scale X",
            value = transform.scaleX,
            onValueChange = { 
                val newScaleY = if (uniformScale) it else transform.scaleY
                val t = if (transform.scaleTrack.hasKeyframes()) transform.addScaleKeyframe(currentFrame, it, newScaleY) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(scaleX = it, scaleY = newScaleY)))
            },
            valueRange = 0f..5f,
            isKeyframed = transform.scaleTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.scaleTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.scaleTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(scaleTrack = transform.scaleTrack.copy(keyframes = transform.scaleTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addScaleKeyframe(currentFrame, transform.scaleX, transform.scaleY))
                }
            }
        )
        
        TransformControlRow(
            label = "Scale Y",
            value = transform.scaleY,
            onValueChange = { 
                val newScaleX = if (uniformScale) it else transform.scaleX
                val t = if (transform.scaleTrack.hasKeyframes()) transform.addScaleKeyframe(currentFrame, newScaleX, it) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(scaleY = it, scaleX = newScaleX)))
            },
            valueRange = 0f..5f,
            isKeyframed = transform.scaleTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.scaleTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.scaleTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(scaleTrack = transform.scaleTrack.copy(keyframes = transform.scaleTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addScaleKeyframe(currentFrame, transform.scaleX, transform.scaleY))
                }
            }
        )

        TransformControlRow(
            label = "Rotation",
            value = transform.rotationDegrees,
            onValueChange = { 
                val t = if (transform.rotationTrack.hasKeyframes()) transform.addRotationKeyframe(currentFrame, it) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(rotationDegrees = it)))
            },
            valueRange = -360f..360f,
            isKeyframed = transform.rotationTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.rotationTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.rotationTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(rotationTrack = transform.rotationTrack.copy(keyframes = transform.rotationTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addRotationKeyframe(currentFrame, transform.rotationDegrees))
                }
            }
        )

        TransformControlRow(
            label = "Opacity",
            value = transform.opacity,
            onValueChange = { 
                val t = if (transform.opacityTrack.hasKeyframes()) transform.addOpacityKeyframe(currentFrame, it) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(opacity = it)))
            },
            valueRange = 0f..1f,
            isKeyframed = transform.opacityTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.opacityTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.opacityTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(opacityTrack = transform.opacityTrack.copy(keyframes = transform.opacityTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addOpacityKeyframe(currentFrame, transform.opacity))
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Flip & Fill", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = transform.flipHorizontal, onCheckedChange = { timelineViewModel.updateClipTransform(clipId, transform.copy(transformParams = transform.transformParams.copy(flipHorizontal = it))) })
                Text("Flip H", style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = transform.flipVertical, onCheckedChange = { timelineViewModel.updateClipTransform(clipId, transform.copy(transformParams = transform.transformParams.copy(flipVertical = it))) })
                Text("Flip V", style = MaterialTheme.typography.labelMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { timelineViewModel.updateClipTransform(clipId, ClipTransform()) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        ) {
            Text("Reset Transform")
        }
    }
}
