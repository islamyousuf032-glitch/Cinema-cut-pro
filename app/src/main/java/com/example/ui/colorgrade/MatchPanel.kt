package com.example.ui.colorgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.core.ClipType
import com.example.timeline.ui.TimelineViewModel
import com.example.ui.ColorMatchViewModel

@Composable
fun MatchPanel(
    viewModel: ColorMatchViewModel,
    timelineViewModel: TimelineViewModel,
    modifier: Modifier = Modifier
) {
    val params by viewModel.colorMatchParams.collectAsState()
    val referenceClipId by viewModel.referenceClipId.collectAsState()
    val isMatching by viewModel.isMatching.collectAsState()
    val timelineState by timelineViewModel.uiState.collectAsState()

    val targetClipId = timelineState.selectedClipId
    val targetClip = timelineState.project.tracks.flatMap { it.clips }.find { it.id == targetClipId }

    // Collect available reference clips (excluding current target)
    val availableReferenceClips = timelineState.project.tracks
        .flatMap { it.clips }
        .filter { it.type == ClipType.MEDIA && it.id != targetClipId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Shot Matching", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (targetClip == null) {
            Text("Select a clip in the timeline to match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        Text("Target: ${targetClip.name}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Select Reference Clip", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (availableReferenceClips.isEmpty()) {
            Text("No other clips available to use as reference.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableReferenceClips) { clip ->
                    val isSelected = clip.id == referenceClipId
                    Card(
                        modifier = Modifier
                            .width(100.dp)
                            .height(60.dp)
                            .clickable { viewModel.setReferenceClipId(clip.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = clip.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Match Parameters", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.matchExposure,
                onCheckedChange = { viewModel.updateParams(params.copy(matchExposure = it)) }
            )
            Text("Match Exposure")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.matchContrast,
                onCheckedChange = { viewModel.updateParams(params.copy(matchContrast = it)) }
            )
            Text("Match Contrast")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.matchWhiteBalance,
                onCheckedChange = { viewModel.updateParams(params.copy(matchWhiteBalance = it)) }
            )
            Text("Match White Balance")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.matchSaturation,
                onCheckedChange = { viewModel.updateParams(params.copy(matchSaturation = it)) }
            )
            Text("Match Saturation")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Match Strength: ${(params.matchStrength * 100).toInt()}%")
        Slider(
            value = params.matchStrength,
            onValueChange = { viewModel.updateParams(params.copy(matchStrength = it)) },
            valueRange = 0f..1f
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.performMatch() },
            enabled = referenceClipId != null && !isMatching,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isMatching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Matching...")
            } else {
                Text("Apply Match")
            }
        }
    }
}
