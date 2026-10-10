package com.example.ui.editor.viewport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.ui.TimelineViewModel

@Composable
fun ViewportControlBar(
    timelineViewModel: TimelineViewModel,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    showSafeArea: Boolean,
    onShowSafeAreaChanged: (Boolean) -> Unit,
    showGuideOverlay: Boolean,
    onShowGuideOverlayChanged: (Boolean) -> Unit,
    timecodeText: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by timelineViewModel.uiState.collectAsState()
    val activeClip = remember(uiState) {
        val cid = uiState.selectedClipId
        uiState.project.tracks.flatMap { it.clips }.find { it.id == cid }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable left/middle section
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timecodeText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                // Transforms
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TransformButton("Fit") {
                        activeClip?.let {
                            val tp = it.transform.transformParams.copy(scaleX = 1f, scaleY = 1f, positionX = 0f, positionY = 0f, rotationDegrees = 0f)
                            timelineViewModel.updateClipTransform(it.id, it.transform.copy(transformParams = tp))
                        }
                    }
                    TransformButton("Fill") {
                        activeClip?.let {
                            val tp = it.transform.transformParams.copy(scaleX = 1.5f, scaleY = 1.5f, positionX = 0f, positionY = 0f, rotationDegrees = 0f)
                            timelineViewModel.updateClipTransform(it.id, it.transform.copy(transformParams = tp))
                        }
                    }
                    TransformButton("Stretch") {
                        activeClip?.let {
                            val tp = it.transform.transformParams.copy(scaleX = 1.25f, scaleY = 1f, positionX = 0f, positionY = 0f, rotationDegrees = 0f)
                            timelineViewModel.updateClipTransform(it.id, it.transform.copy(transformParams = tp))
                        }
                    }
                    TransformButton("Reset") {
                        activeClip?.let {
                            timelineViewModel.updateClipTransform(it.id, com.example.timeline.core.transform.ClipTransform())
                        }
                    }
                }

                // Overlays
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = showSafeArea,
                        onClick = { onShowSafeAreaChanged(!showSafeArea) },
                        label = { Text("Safe", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                    FilterChip(
                        selected = showGuideOverlay,
                        onClick = { onShowGuideOverlayChanged(!showGuideOverlay) },
                        label = { Text("Guide", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Viewport Settings",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fixed right section for Play button
            IconButton(
                onClick = onTogglePlay,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TransformButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.height(24.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
