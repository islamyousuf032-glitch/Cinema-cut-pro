package com.example.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdvancedTimelineTopBar(
    rippleAllTracks: Boolean,
    onToggleRippleAllTracks: () -> Unit,
    projectName: String,
    playheadFrame: Long,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    snapEnabled: Boolean,
    onToggleSnap: () -> Unit,
    linkedSelection: Boolean,
    onToggleLinked: () -> Unit,
    showMiniViewport: Boolean,
    onToggleMiniViewport: () -> Unit
) {
    Surface(
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = projectName.ifEmpty { "Untitled Project" },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // Timecode
            Text(
                text = TimecodeFormatter.formatTimecode(playheadFrame, com.example.timeline.core.Rational(30, 1)), // assuming 30 fps for now
                color = Color(0xFF00FF00),
                fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .background(Color.Black, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Color.White)
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = Color.White)
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 8.dp), color = Color.Gray)

            IconToggleButton(checked = snapEnabled, onCheckedChange = { onToggleSnap() }) {
                Icon(Icons.Default.PushPin, "Snap", tint = if (snapEnabled) MaterialTheme.colorScheme.primary else Color.White)
            }
            IconToggleButton(checked = linkedSelection, onCheckedChange = { onToggleLinked() }) {
                Icon(Icons.Default.Link, "Link Selection", tint = if (linkedSelection) MaterialTheme.colorScheme.primary else Color.White)
            }
            IconToggleButton(checked = rippleAllTracks, onCheckedChange = { onToggleRippleAllTracks() }) {
                Icon(Icons.Default.Layers, "Ripple All Tracks", tint = if (rippleAllTracks) MaterialTheme.colorScheme.primary else Color.White)
            }
            IconToggleButton(checked = showMiniViewport, onCheckedChange = { onToggleMiniViewport() }) {
                Icon(Icons.Default.PlayArrow, "Toggle Viewport", tint = if (showMiniViewport) MaterialTheme.colorScheme.primary else Color.White)
            }
            
            IconButton(onClick = { /* TODO settings */ }) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
            }
        }
    }
}
