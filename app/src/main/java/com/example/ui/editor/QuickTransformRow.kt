package com.example.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.ui.TimelineViewModel

@Composable
fun QuickTransformRow(
    timelineViewModel: TimelineViewModel,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransformButton("Fit") {
                    activeClip?.let {
                        val tp = it.transform.transformParams.copy(scaleX = 1f, scaleY = 1f, positionX = 0f, positionY = 0f, rotationDegrees = 0f)
                        val newTransform = it.transform.copy(transformParams = tp)
                        timelineViewModel.updateClipTransform(it.id, newTransform)
                    }
                }
                TransformButton("Fill") {
                    activeClip?.let {
                        val tp = it.transform.transformParams.copy(scaleX = 1.5f, scaleY = 1.5f, positionX = 0f, positionY = 0f, rotationDegrees = 0f)
                        val newTransform = it.transform.copy(transformParams = tp)
                        timelineViewModel.updateClipTransform(it.id, newTransform)
                    }
                }
                TransformButton("Stretch") {
                    activeClip?.let {
                        val tp = it.transform.transformParams.copy(scaleX = 1.25f, scaleY = 1f, positionX = 0f, positionY = 0f, rotationDegrees = 0f)
                        val newTransform = it.transform.copy(transformParams = tp)
                        timelineViewModel.updateClipTransform(it.id, newTransform)
                    }
                }
                TransformButton("Reset") {
                    activeClip?.let {
                        val newTransform = com.example.timeline.core.transform.ClipTransform()
                        timelineViewModel.updateClipTransform(it.id, newTransform)
                    }
                }
            }
        }
    }
}

@Composable
fun TransformButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(28.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
