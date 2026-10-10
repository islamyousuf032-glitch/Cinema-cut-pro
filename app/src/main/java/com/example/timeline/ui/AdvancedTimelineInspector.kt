package com.example.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.core.TimelineProject

@Composable
fun AdvancedTimelineInspector(
    activeTool: TimelineTool,
    selectedClipId: String?,
    project: TimelineProject
) {
    Surface(
        color = Color(0xFF1E1E1E),
        modifier = Modifier.width(260.dp).fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Inspector",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            
            HorizontalDivider(color = Color.DarkGray)

            Text(
                "Active Tool: ${activeTool.name}",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            HorizontalDivider(color = Color.DarkGray)

            if (selectedClipId != null) {
                val clip = project.tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                if (clip != null) {
                    Text("Selected Clip", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text("Name: ${clip.name}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Timeline Start: ${clip.timelineStart}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Duration: ${clip.sourceOut - clip.sourceIn}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Source In: ${clip.sourceIn}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Source Out: ${clip.sourceOut}", color = Color.LightGray, fontSize = 12.sp)
                } else {
                    Text("Selected clip not found", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                Text(
                    "No clip selected",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
