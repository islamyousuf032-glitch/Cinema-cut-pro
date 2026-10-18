package com.example.timeline.engine.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorPerformanceOverlay(modifier: Modifier = Modifier) {
    val stats by FrameTimeProfiler.stats.collectAsState()
    val nativeStats by FrameTimeProfiler.nativeStats.collectAsState()

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp)
    ) {
        val style = MaterialTheme.typography.bodySmall.copy(
            color = Color.Green,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp
        )
        Text("UI FPS: ${"%.1f".format(stats.uiFps)}", style = style)
        Text("Preview FPS: ${"%.1f".format(stats.previewFps)}", style = style)
        Text("Frame Time: ${"%.1f".format(stats.frameTimeMs)} ms", style = style)
        Text("Dropped: ${stats.droppedFrames}", style = style)
        Text("Native Transform Time: ${"%.2f".format(nativeStats.nativeTransformTimeMs)} ms", style = style)
        Text("Engine: ${stats.activeEngine}", style = style)
        Text("Memory: ${"%.1f".format(stats.memoryUsageMb)} MB", style = style)
        Text("Playhead Update: ${"%.1f".format(stats.playheadUpdateRateMs)} ms", style = style)
    }
}
