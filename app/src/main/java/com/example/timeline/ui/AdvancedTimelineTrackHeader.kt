package com.example.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.core.TimelineTrack

@Composable
fun AdvancedTimelineTrackHeader(
    track: TimelineTrack,
    trackIndex: Int,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF252525),
        contentColor = Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.name,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { onRename() }
                )
                
                Row {
                    IconButton(onClick = onToggleLock, modifier = Modifier.size(20.dp)) {
                        Icon(if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock", tint = if (track.isLocked) Color.Red else Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onToggleVisibility, modifier = Modifier.size(20.dp)) {
                        Icon(if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visibility", tint = if (track.isVisible) Color.Gray else Color.DarkGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (track.type == com.example.timeline.core.TrackType.AUDIO) "A${trackIndex + 1}" else "V${trackIndex + 1}", fontSize = 10.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    if (track.type == com.example.timeline.core.TrackType.AUDIO) {
                        IconButton(onClick = onToggleMute, modifier = Modifier.size(20.dp)) {
                            Text("M", fontSize = 10.sp, color = if (track.isMuted) Color.Red else Color.Gray)
                        }
                        IconButton(onClick = onToggleSolo, modifier = Modifier.size(20.dp)) {
                            Text("S", fontSize = 10.sp, color = if (track.isSolo) Color.Yellow else Color.Gray)
                        }
                    }
                }
                
                Row {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move Up", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move Down", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
