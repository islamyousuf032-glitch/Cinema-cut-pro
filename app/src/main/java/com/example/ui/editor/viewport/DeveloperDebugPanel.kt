package com.example.ui.editor.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.media.MediaAsset
import com.example.timeline.engine.performance.FrameTimeProfiler
import com.example.timeline.engine.performance.PerformanceMonitor

@Composable
fun DeveloperDebugPanel(
    currentAsset: MediaAsset?,
    playheadFrame: Long,
    engineType: String,
    modifier: Modifier = Modifier
) {
    PerformanceMonitor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        Text("Developer Debug", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Playhead: $playheadFrame", color = Color.White, fontSize = 10.sp)
        Text("Engine: $engineType", color = Color.White, fontSize = 10.sp)
        if (currentAsset != null) {
            Text("URL: ${currentAsset.localOriginalUriString ?: currentAsset.originalUriString}", color = Color.White, fontSize = 10.sp)
        }
        
        val stats = FrameTimeProfiler.stats.collectAsState().value
        Text("FPS: ${"%.1f".format(stats.uiFps)}", color = Color.White, fontSize = 10.sp)
        Text("Frame Time: ${"%.1f".format(stats.frameTimeMs)}ms (avg) / Memory: ${"%.1f".format(stats.memoryUsageMb)}MB", color = Color.White, fontSize = 10.sp)
        Text("Dropped Frames: ${stats.droppedFrames}", color = Color.White, fontSize = 10.sp)
        val nativeTime = com.example.timeline.engine.native.NativeTimelineMetrics.lastLayoutTimeMs.collectAsState().value
        Text("Native Layout: ${nativeTime}ms (Available: ${com.example.timeline.engine.native.NativeTimelineCore.isAvailable()})", color = Color.White, fontSize = 10.sp)
    }
}
