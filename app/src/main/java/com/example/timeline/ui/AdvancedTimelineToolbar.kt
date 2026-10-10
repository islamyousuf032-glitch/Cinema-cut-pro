package com.example.timeline.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AdvancedTimelineToolbar(
    activeTool: TimelineTool,
    onToolSelect: (TimelineTool) -> Unit,
    snapEnabled: Boolean,
    onToggleSnap: () -> Unit,
    linkedSelection: Boolean,
    onToggleLinked: () -> Unit,
    onClose: () -> Unit,
    showMiniViewport: Boolean,
    onToggleMiniViewport: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBlade: () -> Unit,
    onDelete: () -> Unit,
    onRippleDelete: () -> Unit,
    onNudgeLeft: () -> Unit,
    onNudgeRight: () -> Unit,
    onMoveUpTrack: () -> Unit,
    onMoveDownTrack: () -> Unit,
    onTrimToPlayheadStart: () -> Unit,
    onTrimToPlayheadEnd: () -> Unit,
    onExtendEdit: () -> Unit
) {
    Surface(
        color = Color(0xFF222222),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close Advanced Timeline", tint = Color.White) }
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
            
            IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Undo", tint = Color.White) }
            IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Redo", tint = Color.White) }
            
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
            
            ToolButton(activeTool == TimelineTool.SELECTION, { onToolSelect(TimelineTool.SELECTION) }, "A", "Selection")
            ToolButton(activeTool == TimelineTool.TRIM, { onToolSelect(TimelineTool.TRIM) }, "T", "Trim")
            ToolButton(activeTool == TimelineTool.BLADE, { onToolSelect(TimelineTool.BLADE) }, "B", "Blade")
            ToolButton(activeTool == TimelineTool.RIPPLE, { onToolSelect(TimelineTool.RIPPLE) }, "R", "Ripple")
            ToolButton(activeTool == TimelineTool.ROLL, { onToolSelect(TimelineTool.ROLL) }, "Ro", "Roll")
            ToolButton(activeTool == TimelineTool.SLIP, { onToolSelect(TimelineTool.SLIP) }, "Sl", "Slip")
            ToolButton(activeTool == TimelineTool.SLIDE, { onToolSelect(TimelineTool.SLIDE) }, "Sd", "Slide")

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            IconButton(onClick = onNudgeLeft) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nudge Left", tint = Color.LightGray) }
            IconButton(onClick = onNudgeRight) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Nudge Right", tint = Color.LightGray) }
            IconButton(onClick = onMoveUpTrack) { Icon(Icons.Default.KeyboardArrowUp, "Move Up", tint = Color.LightGray) }
            IconButton(onClick = onMoveDownTrack) { Icon(Icons.Default.KeyboardArrowDown, "Move Down", tint = Color.LightGray) }
            
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
            
            TextButton(onClick = onTrimToPlayheadStart) { Text("[", color = Color.White) }
            TextButton(onClick = onTrimToPlayheadEnd) { Text("]", color = Color.White) }
            TextButton(onClick = onExtendEdit) { Text("Ext", color = Color.White) }
            
            Spacer(Modifier.weight(1f))
            
            IconToggleButton(checked = snapEnabled, onCheckedChange = { onToggleSnap() }) {
                Icon(Icons.Default.PushPin, "Snap", tint = if (snapEnabled) MaterialTheme.colorScheme.primary else Color.White)
            }
            IconToggleButton(checked = linkedSelection, onCheckedChange = { onToggleLinked() }) {
                Icon(Icons.Default.Link, "Link Selection", tint = if (linkedSelection) MaterialTheme.colorScheme.primary else Color.White)
            }
            IconToggleButton(checked = showMiniViewport, onCheckedChange = { onToggleMiniViewport() }) {
                Icon(Icons.Default.PlayArrow, "Toggle Viewport", tint = if (showMiniViewport) MaterialTheme.colorScheme.primary else Color.White)
            }
        }
    }
}

@Composable
fun ToolButton(active: Boolean, onClick: () -> Unit, label: String, tooltip: String) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.height(32.dp).widthIn(min = 40.dp)
    ) {
        Text(label)
    }
}
