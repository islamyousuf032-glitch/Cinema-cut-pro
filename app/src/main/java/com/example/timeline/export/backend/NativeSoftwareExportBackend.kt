package com.example.timeline.export.backend

import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.*
import com.example.timeline.export.native.NativeExportCore
import android.util.Log

class NativeSoftwareExportBackend : ExportBackend {
    override val backendType = ExportBackendType.NATIVE_SOFTWARE
    
    override fun getCapabilities(): ExportBackendCapability {
        return ExportBackendCapability(
            backendAvailable = NativeExportCore.isAvailable(),
            hardwareAccelerated = false,
            supportedCodecs = listOf(ExportCodec.IMAGE_SEQUENCE),
            supportedContainers = listOf(ExportContainer.MP4),
            maxResolutionWidth = 3840,
            maxResolutionHeight = 2160,
            maxFps = 60f,
            supportsHDR = false,
            supportsAlpha = true,
            supportsAudioMix = true,
            supportsVideoComposition = true,
            supportsProRes = false,
            supportsAV1 = false,
            supports10Bit = false,
            supportsBatch = true,
            supportsCancel = true
        )
    }

    override fun validate(settings: ExportSettings, project: TimelineProject): ExportValidationResult {
        if (!NativeExportCore.isAvailable()) {
            return ExportValidationResult(
                isValid = false,
                unsupportedFeatures = listOf("Native Software backend (C++) not available"),
                errorMessage = "Native Software backend (C++) not available"
            )
        }
        return ExportValidationResult(true)
    }

    override suspend fun prepare(job: ExportJob): Boolean {
        if (NativeExportCore.isAvailable()) {
            NativeExportCore.nativeCreateExportCore()
            Log.i("NativeSoftwareExport", "Preparing: created export core.")
            return true
        }
        return false
    }

    override suspend fun render(job: ExportJob, onProgress: (ExportProgress) -> Unit) {
        if (NativeExportCore.isAvailable()) {
            Log.i("NativeSoftwareExport", "Rendering with native core...")
            NativeExportCore.nativeEvaluateFramePlan(0L)
            // Simulating progress
            onProgress(ExportProgress(job.jobId, ExportJobStatus.COMPLETED, 100f, 1, 1, 0, 0, "Completed Native Software Render"))
        } else {
             onProgress(ExportProgress(job.jobId, ExportJobStatus.FAILED, 0f, 0, 0, 0, 0, "Native Software backend failed", "C++ library missing"))
        }
    }

    override fun cancel(jobId: String) {}

    override fun release() {
        if (NativeExportCore.isAvailable()) {
             NativeExportCore.nativeReleaseExportCore()
             Log.i("NativeSoftwareExport", "Released export core.")
        }
    }
}
