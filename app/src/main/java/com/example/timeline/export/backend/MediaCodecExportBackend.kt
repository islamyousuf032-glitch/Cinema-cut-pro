package com.example.timeline.export.backend

import android.content.Context
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.*
import com.example.timeline.export.pipeline.VideoExportPipeline
import com.example.timeline.export.pipeline.ExportCancellationToken
import com.example.timeline.export.pipeline.ExportProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MediaCodecExportBackend(private val context: Context) : ExportBackend {
    override val backendType = ExportBackendType.MEDIACODEC_HARDWARE
    
    private val cancellationTokens = mutableMapOf<String, ExportCancellationToken>()
    private var projectRef: TimelineProject? = null
    
    override fun getCapabilities(): ExportBackendCapability {
        return ExportBackendCapability(
            backendAvailable = true,
            hardwareAccelerated = true,
            supportedCodecs = listOf(ExportCodec.H264, ExportCodec.H265, ExportCodec.AV1),
            supportedContainers = listOf(ExportContainer.MP4, ExportContainer.MKV), // MediaMuxer supports limited MP4/WebM usually. Assuming MKV might be partial.
            maxResolutionWidth = 3840,
            maxResolutionHeight = 2160,
            maxFps = 120f,
            supportsHDR = true,
            supportsAlpha = false,
            supportsAudioMix = true,
            supportsVideoComposition = true,
            supportsProRes = false,
            supportsAV1 = true,
            supports10Bit = true,
            supportsBatch = true,
            supportsCancel = true
        )
    }
    
    override fun validate(settings: ExportSettings, project: TimelineProject): ExportValidationResult {
        projectRef = project
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val cap = getCapabilities()
        
        if (!cap.backendAvailable) errors.add("MediaCodec backend not available")
        if (settings.codec !in cap.supportedCodecs) errors.add("Codec \${settings.codec} not supported by MediaCodec")
        if (settings.container !in cap.supportedContainers) errors.add("Container \${settings.container} not supported by MediaCodec")
        if (settings.resolutionWidth > cap.maxResolutionWidth || settings.resolutionHeight > cap.maxResolutionHeight) {
            errors.add("Resolution \${settings.resolutionWidth}x\${settings.resolutionHeight} exceeds maximum limits")
        }
        
        return ExportValidationResult(
            isValid = errors.isEmpty(),
            unsupportedFeatures = errors,
            warnings = warnings,
            errorMessage = errors.firstOrNull()
        )
    }
    
    override suspend fun prepare(job: ExportJob): Boolean {
        return true
    }
    
    override suspend fun render(job: ExportJob, onProgress: (ExportProgress) -> Unit) {
        val proj = projectRef ?: run {
            onProgress(ExportProgress(job.jobId, ExportJobStatus.FAILED, 0f, 0, 0, 0, 0, "Project reference not set"))
            return
        }

        val cancellationToken = ExportCancellationToken()
        cancellationTokens[job.jobId] = cancellationToken
        
        // Output path needs to be available. We will assume getting one from context scope or it's passed somehow.
        val outputFile = if (job.outputUri != null) {
            java.io.File(job.outputUri)
        } else {
            java.io.File(context.cacheDir, "export_\${System.currentTimeMillis()}.mp4")
        }
        
        // Let ProgressTracker calculate it based on clips
        val maxTimelineEnd = proj.tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEnd } ?: 1L
        val maxDurationUs = (maxTimelineEnd / proj.settings.getFpsRational().toFloat()) * 1_000_000
        val totalFrames = (maxDurationUs / 1000000.0 * job.settings.frameRate.floatValue).toLong().coerceAtLeast(1L)
        
        val progressTracker = ExportProgressTracker(job.jobId, totalFrames) { progress ->
            onProgress(progress)
        }

        val pipeline = VideoExportPipeline(context, proj)
        pipeline.runExport(job, outputFile.absolutePath, cancellationToken, progressTracker)
        
        cancellationTokens.remove(job.jobId)
    }
    
    override fun cancel(jobId: String) {
        cancellationTokens[jobId]?.cancel()
    }
    
    override fun release() {
        cancellationTokens.values.forEach { it.cancel() }
        cancellationTokens.clear()
        projectRef = null
    }

}