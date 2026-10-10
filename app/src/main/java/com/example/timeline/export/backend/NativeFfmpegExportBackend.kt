package com.example.timeline.export.backend

import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.*

class NativeFfmpegExportBackend : ExportBackend {
    override val backendType = ExportBackendType.NATIVE_FFMPEG
    
    // We intentionally set this to false to reflect reality: no FFmpeg binary is loaded yet.
    private val isFfmpegAvailable = false 

    override fun getCapabilities(): ExportBackendCapability {
        return ExportBackendCapability(
            backendAvailable = isFfmpegAvailable,
            hardwareAccelerated = false,
            supportedCodecs = listOf(ExportCodec.H264, ExportCodec.H265, ExportCodec.PRORES, ExportCodec.AV1, ExportCodec.IMAGE_SEQUENCE),
            supportedContainers = listOf(ExportContainer.MP4, ExportContainer.MOV, ExportContainer.MKV),
            maxResolutionWidth = 8192,
            maxResolutionHeight = 4320,
            maxFps = 120f,
            supportsHDR = true,
            supportsAlpha = true,
            supportsAudioMix = true,
            supportsVideoComposition = true,
            supportsProRes = true,
            supportsAV1 = true,
            supports10Bit = true,
            supportsBatch = true,
            supportsCancel = true
        )
    }
    
    override fun validate(settings: ExportSettings, project: TimelineProject): ExportValidationResult {
        if (!isFfmpegAvailable) {
            return ExportValidationResult(
                isValid = false,
                unsupportedFeatures = listOf("FFmpeg backend not installed"),
                errorMessage = "FFmpeg backend not installed"
            )
        }
        return ExportValidationResult(true)
    }
    
    override suspend fun prepare(job: ExportJob): Boolean {
        return isFfmpegAvailable
    }
    
    override suspend fun render(job: ExportJob, onProgress: (ExportProgress) -> Unit) {
        if (!isFfmpegAvailable) {
            onProgress(ExportProgress(job.jobId, ExportJobStatus.FAILED, 0f, 0, 0, 0, 0, "FFmpeg backend not installed.", "FFmpeg Missing"))
            return
        }
    }
    
    override fun cancel(jobId: String) {}
    
    override fun release() {}
}
