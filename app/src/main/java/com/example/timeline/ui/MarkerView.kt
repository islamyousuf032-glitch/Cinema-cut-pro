package com.example.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeline.core.TimelineMarker

@Composable
fun MarkerView(marker: TimelineMarker, pixelsPerFrame: Float) {
    Box(
        modifier = Modifier
            .absoluteOffset(x = (marker.frame * pixelsPerFrame).dp)
            .width(2.dp)
            .fillMaxHeight()
            .background(
                try {
                    Color(android.graphics.Color.parseColor(marker.color))
                } catch(e: Exception) {
                    Color.Red
                }
            )
            .padding(top = 30.dp)
    ) {
        Text(
            text = marker.name,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.background(Color.Black.copy(alpha=0.5f)).padding(2.dp)
        )
    }
}
