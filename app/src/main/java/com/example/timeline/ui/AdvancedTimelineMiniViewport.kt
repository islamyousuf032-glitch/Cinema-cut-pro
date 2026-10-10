package com.example.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.editor.EditorViewModel
import com.example.ui.editor.VideoViewportSection
import com.example.timeline.engine.preview.TimelinePreviewController

@Composable
fun AdvancedTimelineMiniViewport(
    timelineUiState: TimelineUiState,
    timelineViewModel: TimelineViewModel,
    editorViewModel: EditorViewModel,
    timelinePreviewController: TimelinePreviewController,
    playheadFrame: Long,
    isPlaying: Boolean,
    onRelinkRequest: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        VideoViewportSection(
            timelineUiState = timelineUiState,
            timelineViewModel = timelineViewModel,
            editorViewModel = editorViewModel,
            timelinePreviewController = timelinePreviewController,
            playheadFrame = playheadFrame,
            isPlaying = isPlaying,
            isSampling = false,
            onColorSampled = { _,_,_ -> },
            onRelinkRequest = onRelinkRequest,
            modifier = Modifier.fillMaxSize()
        )
        
        // Transparent overlay to intercept taps and toggle play/pause
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    timelineViewModel.togglePlay()
                }
        ) {
            if (!isPlaying) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
        }

        // Playhead frame text
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Frame: $playheadFrame",
                color = Color.White,
                fontSize = 10.sp
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
    }
}
