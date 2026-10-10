package com.example.timeline.engine.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
fun PerformanceMonitor() {
    DisposableEffect(Unit) {
        FrameTimeProfiler.start()
        onDispose {
            FrameTimeProfiler.stop()
        }
    }
}
