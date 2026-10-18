package com.example.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun TimelineToolbar(
    onImportClick: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onRippleDelete: () -> Unit,
    onAddMarker: () -> Unit,
    onAddTrack: () -> Unit = {},
    onCompound: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    hasSelection: Boolean,
    isFollowing: Boolean = true,
    onToggleFollow: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Undo") }
        IconButton(onClick = onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Redo") }
        
        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
        
        Button(
            onClick = onImportClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("Import Media")
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onDelete, enabled = hasSelection) { Icon(Icons.Default.Delete, "Delete") }
        IconButton(onClick = onAddMarker) { Icon(Icons.Default.LocationOn, "Add Marker") }

        IconToggleButton(checked = isFollowing, onCheckedChange = { onToggleFollow() }) {
            Icon(Icons.Default.PlayArrow, "Follow Playhead", tint = if (isFollowing) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        
        TextButton(onClick = onZoomOut) { Text("-") }
        TextButton(onClick = onZoomIn) { Text("+") }
    }
}


