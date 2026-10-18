package com.example.timeline.ui.viewport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.core.transform.TransformFitMode
import com.example.timeline.core.transform.BlendMode
import com.example.timeline.core.transform.PerspectiveParams

@Composable
fun ClipTransformControls(
    clip: TimelineClip?,
    activeOverlayMode: String = "BoundingBox",
    onOverlayModeChanged: (String) -> Unit = {},
    onTransformChanged: (ClipTransform) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clip == null) {
        return
    }

    val transform = clip.transform
    var showBlendMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            item { Text("Transform:", style = MaterialTheme.typography.labelSmall) }
            
            val modes = listOf("BoundingBox", "Crop", "Perspective")
            modes.forEach { mode ->
                item {
                    FilterChip(
                        selected = activeOverlayMode == mode,
                        onClick = { 
                            onOverlayModeChanged(mode) 
                            if (mode == "Perspective" && !transform.perspectiveParams.enabled) {
                                onTransformChanged(transform.copy(perspectiveParams = PerspectiveParams(enabled = true)))
                            }
                        },
                        label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.width(8.dp)) }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = transform.motionBlurParams.enabled,
                        onCheckedChange = { 
                            onTransformChanged(transform.copy(motionBlurParams = transform.motionBlurParams.copy(enabled = it)))
                        }
                    )
                    Text("Motion Blur", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (transform.motionBlurParams.enabled) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Str:", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = transform.motionBlurParams.strength,
                            onValueChange = { onTransformChanged(transform.copy(motionBlurParams = transform.motionBlurParams.copy(strength = it))) },
                            valueRange = 0f..2f,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shutter:", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = transform.motionBlurParams.shutterAngle,
                            onValueChange = { onTransformChanged(transform.copy(motionBlurParams = transform.motionBlurParams.copy(shutterAngle = it))) },
                            valueRange = 0f..360f,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }

            item {
                Box {
                    TextButton(onClick = { showBlendMenu = true }, contentPadding = PaddingValues(4.dp)) {
                        Text("Blend: ${transform.blendMode.name}", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = showBlendMenu, onDismissRequest = { showBlendMenu = false }) {
                        BlendMode.values().forEach { bm ->
                            DropdownMenuItem(
                                text = { Text(bm.name) },
                                onClick = {
                                    onTransformChanged(transform.copy(blendMode = bm))
                                    showBlendMenu = false
                                }
                            )
                        }
                    }
                }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Op:", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = transform.opacity,
                        onValueChange = { onTransformChanged(transform.copy(transformParams = transform.transformParams.copy(opacity = it))) },
                        valueRange = 0f..1f,
                        modifier = Modifier.width(60.dp)
                    )
                }
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            TextButton(
                onClick = { onTransformChanged(transform.copy(fitMode = TransformFitMode.FIT)) },
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("Fit", style = MaterialTheme.typography.labelSmall, color = if (transform.fitMode == TransformFitMode.FIT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            TextButton(
                onClick = { onTransformChanged(transform.copy(fitMode = TransformFitMode.FILL)) },
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("Fill", style = MaterialTheme.typography.labelSmall, color = if (transform.fitMode == TransformFitMode.FILL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            TextButton(
                onClick = { onTransformChanged(transform.copy(fitMode = TransformFitMode.STRETCH)) },
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("Stretch", style = MaterialTheme.typography.labelSmall, color = if (transform.fitMode == TransformFitMode.STRETCH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
    
            Spacer(modifier = Modifier.weight(1f))
            
            TextButton(onClick = { onTransformChanged(ClipTransform()) }) {
                Text("Reset")
            }
        }
    }
}
