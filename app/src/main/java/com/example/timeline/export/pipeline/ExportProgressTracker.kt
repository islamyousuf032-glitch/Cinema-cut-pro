package com.example.timeline.export.pipeline

import com.example.timeline.export.model.ExportProgress
import com.example.timeline.export.model.ExportJobStatus

class ExportProgressTracker(
    val jobId: String,
    val totalFrames: Long,
    val onProgress: (ExportProgress) -> Unit
) {
    private var processedFrames = 0L
    private val startTime = System.currentTimeMillis()
    
    fun updateProgress(frames: Long) {
        processedFrames += frames
        val percent = if (totalFrames > 0) (processedFrames.toFloat() / totalFrames) * 100f else 0f
        
        // Calculate ETA
        val elapsed = System.currentTimeMillis() - startTime
        val avgTimePerFrame = if (processedFrames > 0) elapsed / processedFrames else 0
        val remainingFrames = totalFrames - processedFrames
        val etaMs = remainingFrames * avgTimePerFrame
        
        onProgress(ExportProgress(
            jobId = jobId,
            status = ExportJobStatus.RENDERING_VIDEO,
            progressPercent = percent,
            renderedFrames = processedFrames,
            totalFrames = totalFrames,
            elapsedMs = elapsed,
            estimatedRemainingMs = etaMs,
            currentStage = "Exporting..."
        ))
    }
    
    fun complete() {
        val elapsed = System.currentTimeMillis() - startTime
        onProgress(ExportProgress(
            jobId = jobId,
            status = ExportJobStatus.COMPLETED,
            progressPercent = 100f,
            renderedFrames = totalFrames,
            totalFrames = totalFrames,
            elapsedMs = elapsed,
            estimatedRemainingMs = 0L,
            currentStage = "Export completed successfully"
        ))
    }
    
    fun fail(error: String, localized: String? = null) {
        val elapsed = System.currentTimeMillis() - startTime
        onProgress(ExportProgress(
            jobId = jobId,
            status = ExportJobStatus.FAILED,
            progressPercent = 0f,
            renderedFrames = processedFrames,
            totalFrames = totalFrames,
            elapsedMs = elapsed,
            estimatedRemainingMs = 0L,
            currentStage = "Failed",
            errorMessage = error
        ))
    }
}
