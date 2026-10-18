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
        
        if (com.example.BuildConfig.DEBUG) {
            Log.d("ExportPerf", "Duration: ${durationSec}s")
            Log.d("ExportPerf", "Frames rendered: $framesProcessed ($renderFps FPS)")
            Log.d("ExportPerf", "Frames encoded: $framesEncoded ($encodeFps FPS)")
            Log.d("ExportPerf", "Frames dropped: $framesDropped")
            Log.d("ExportPerf", "Native stats: $nativeStats")
        }
        
        return ExportBenchmark(
            durationMs = durationMs,
            renderFps = renderFps,
            encodeFps = encodeFps,
            framesDropped = framesDropped,
            nativeStats = nativeStats.copy()
        )
    }
}
