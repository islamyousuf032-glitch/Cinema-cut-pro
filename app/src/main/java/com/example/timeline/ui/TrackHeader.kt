package com.example.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeline.core.TimelineTrack

@Composable
fun TrackHeader(track: TimelineTrack) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(track.height.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(4.dp)
    ) {
        Text(
            text = track.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (track.isLocked) {
             Icon(Icons.Default.Lock, "Locked", modifier = Modifier.align(Alignment.BottomEnd).size(16.dp))
        }
    }
}
