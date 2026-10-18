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
import com.example.timeline.core.transform.BlendMode

@Composable
fun MotionPanel(timelineViewModel: TimelineViewModel) {
    val state by timelineViewModel.uiState.collectAsState()
    val clipId = state.selectedClipId
    
    if (clipId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Select a clip to edit motion properties", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Anchor Point", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        TransformControlRow(
            label = "Anchor X",
            value = transform.anchorPointX,
            onValueChange = { 
                val t = if (transform.anchorTrack.hasKeyframes()) transform.addAnchorKeyframe(currentFrame, it, transform.anchorPointY) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(anchorX = it)))
            },
            valueRange = -1000f..1000f,
            isKeyframed = transform.anchorTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.anchorTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.anchorTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(anchorTrack = transform.anchorTrack.copy(keyframes = transform.anchorTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addAnchorKeyframe(currentFrame, transform.anchorPointX, transform.anchorPointY))
                }
            }
        )

        TransformControlRow(
            label = "Anchor Y",
            value = transform.anchorPointY,
            onValueChange = { 
                val t = if (transform.anchorTrack.hasKeyframes()) transform.addAnchorKeyframe(currentFrame, transform.anchorPointX, it) else transform
                timelineViewModel.updateClipTransform(clipId, t.copy(transformParams = t.transformParams.copy(anchorY = it)))
            },
            valueRange = -1000f..1000f,
            isKeyframed = transform.anchorTrack.keyframes.any { it.frame == currentFrame },
            hasAnyKeyframes = transform.anchorTrack.hasKeyframes(),
            onKeyframeToggle = {
                if (transform.anchorTrack.keyframes.any { it.frame == currentFrame }) {
                    timelineViewModel.updateClipTransform(clipId, transform.copy(anchorTrack = transform.anchorTrack.copy(keyframes = transform.anchorTrack.keyframes.filter { it.frame != currentFrame })))
                } else {
                    timelineViewModel.updateClipTransform(clipId, transform.addAnchorKeyframe(currentFrame, transform.anchorPointX, transform.anchorPointY))
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text("Crop", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        TransformControlRow(
            label = "Crop Left",
            value = transform.cropParams.cropLeft,
            onValueChange = { 
                val cp = transform.cropParams.copy(cropLeft = it)
                timelineViewModel.updateClipTransform(clipId, transform.copy(cropParams = cp))
            },
            valueRange = 0f..1000f
        )
        TransformControlRow(
            label = "Crop Right",
            value = transform.cropParams.cropRight,
            onValueChange = { 
                val cp = transform.cropParams.copy(cropRight = it)
                timelineViewModel.updateClipTransform(clipId, transform.copy(cropParams = cp))
            },
            valueRange = 0f..1000f
        )
        TransformControlRow(
            label = "Crop Top",
            value = transform.cropParams.cropTop,
            onValueChange = { 
                val cp = transform.cropParams.copy(cropTop = it)
                timelineViewModel.updateClipTransform(clipId, transform.copy(cropParams = cp))
            },
            valueRange = 0f..1000f
        )
        TransformControlRow(
            label = "Crop Bottom",
            value = transform.cropParams.cropBottom,
            onValueChange = { 
                val cp = transform.cropParams.copy(cropBottom = it)
                timelineViewModel.updateClipTransform(clipId, transform.copy(cropParams = cp))
            },
            valueRange = 0f..1000f
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text("Perspective", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = transform.perspectiveParams.enabled,
                onCheckedChange = { timelineViewModel.updateClipTransform(clipId, transform.copy(perspectiveParams = transform.perspectiveParams.copy(enabled = it))) }
            )
            Text("Enable Perspective", style = MaterialTheme.typography.labelMedium)
        }

        if (transform.perspectiveParams.enabled) {
            TransformControlRow(
                label = "Top Left X", value = transform.perspectiveParams.topLeftX, valueRange = -1000f..1000f,
                onValueChange = { timelineViewModel.updateClipTransform(clipId, transform.copy(perspectiveParams = transform.perspectiveParams.copy(topLeftX = it))) }
            )
            TransformControlRow(
                label = "Top Left Y", value = transform.perspectiveParams.topLeftY, valueRange = -1000f..1000f,
                onValueChange = { timelineViewModel.updateClipTransform(clipId, transform.copy(perspectiveParams = transform.perspectiveParams.copy(topLeftY = it))) }
            )
            // Can add remaining points here later...
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Blend & Motion Blur", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        Text("Blend Mode", style = MaterialTheme.typography.labelMedium)
        var expandedBlendMode by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expandedBlendMode = true }) {
                Text(transform.blendMode.name)
            }
            DropdownMenu(expanded = expandedBlendMode, onDismissRequest = { expandedBlendMode = false }) {
                BlendMode.values().forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.name) }, onClick = {
                        timelineViewModel.updateClipTransform(clipId, transform.copy(blendMode = mode))
                        expandedBlendMode = false
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = transform.motionBlurParams.enabled,
                onCheckedChange = { timelineViewModel.updateClipTransform(clipId, transform.copy(motionBlurParams = transform.motionBlurParams.copy(enabled = it))) }
            )
            Text("Enable Motion Blur", style = MaterialTheme.typography.labelMedium)
        }
        
        if (transform.motionBlurParams.enabled) {
            TransformControlRow(
                label = "Strength",
                value = transform.motionBlurParams.strength,
                onValueChange = { 
                    timelineViewModel.updateClipTransform(clipId, transform.copy(motionBlurParams = transform.motionBlurParams.copy(strength = it)))
                },
                valueRange = 0f..100f
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { timelineViewModel.updateClipTransform(clipId, transform.resetCrop()) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
        ) {
            Text("Reset Motion")
        }
    }
}
