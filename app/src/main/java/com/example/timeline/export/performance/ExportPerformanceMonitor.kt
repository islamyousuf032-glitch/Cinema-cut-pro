package com.example.timeline.export.performance

import android.util.Log

class ExportPerformanceMonitor {
    private var startTimeMs = 0L
    private var lastReportTimeMs = 0L
    private var framesProcessed = 0L
    private var framesEncoded = 0L
    private var framesDropped = 0L
    
    val nativeStats = NativeExportStats()
    
    fun start() {
        startTimeMs = System.currentTimeMillis()
        lastReportTimeMs = startTimeMs
        framesProcessed = 0L
        framesEncoded = 0L
        framesDropped = 0L
        nativeStats.reset()
    }
    
    fun onFrameProcessed() {
        framesProcessed++
    }
    
    fun onFrameEncoded() {
        framesEncoded++
    }
    
    fun onFrameDropped() {
        framesDropped++
    }
    
    fun generateReport(): ExportBenchmark {
        val endTimeMs = System.currentTimeMillis()
        val durationMs = endTimeMs - startTimeMs
        
        val durationSec = durationMs / 1000f
        val renderFps = if (durationSec > 0) framesProcessed / durationSec else 0f
        val encodeFps = if (durationSec > 0) framesEncoded / durationSec else 0f
        
        Log.i("ExportPerf", "=== EXPORT PERFORMANCE REPORT ===")
        Log.i("ExportPerf", "Duration: \${durationSec}s")
        Log.i("ExportPerf", "Frames Rendered: \$framesProcessed (\$renderFps FPS)")
        Log.i("ExportPerf", "Frames Encoded: \$framesEncoded (\$encodeFps FPS)")
        Log.i("ExportPerf", "Frames Dropped: \$framesDropped")
        Log.i("ExportPerf", "Native Stats: \$nativeStats")
        
        return ExportBenchmark(
            durationMs = durationMs,
            renderFps = renderFps,
            encodeFps = encodeFps,
            framesDropped = framesDropped,
            nativeStats = nativeStats.copy()
        )
    }
}
