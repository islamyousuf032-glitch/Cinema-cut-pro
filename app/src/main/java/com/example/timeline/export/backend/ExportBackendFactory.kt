package com.example.timeline.export.backend

import android.content.Context
import com.example.timeline.export.model.ExportBackendType

/** Creates only backends that actually produce a valid media file. */
class ExportBackendFactory(private val context: Context) {

    /**
     * MediaCodecExportBackend, NativeSoftwareExportBackend, and the native FFmpeg adapter are not
     * production fallbacks yet. Keep the selectable list limited to the real Media3 implementation
     * so unsupported edits can never appear to succeed via a placeholder renderer.
     */
    fun createAllBackends(): List<ExportBackend> = listOf(Media3TransformerBackend(context))

    fun getDiagnostics(): ExportBackendDiagnostics {
        val media3 = Media3TransformerBackend(context).getCapabilities()
        val ffmpeg = NativeFfmpegExportBackend().getCapabilities()
        return ExportBackendDiagnostics(
            mediaCodecAvailable = media3.backendAvailable,
            ffmpegNativeAvailable = ffmpeg.backendAvailable,
            maxAvailableResolutionWidth = media3.maxResolutionWidth,
            maxAvailableResolutionHeight = media3.maxResolutionHeight,
            hardwareAv1Supported = false,
            availableCodecs = (media3.supportedCodecs + ffmpeg.supportedCodecs.takeIf { ffmpeg.backendAvailable }.orEmpty())
                .distinct()
                .map { it.name },
            mediaCodecCapabilities = media3,
            ffmpegCapabilities = ffmpeg
        )
    }

    fun validateWithAvailableBackend(
        settings: com.example.timeline.export.model.ExportSettings,
        project: com.example.timeline.core.TimelineProject
    ): com.example.timeline.export.model.ExportValidationResult {
        val backend = createAllBackends().firstOrNull { it.backendType == ExportBackendType.MEDIA3_TRANSFORMER }
            ?: return com.example.timeline.export.model.ExportValidationResult(
                isValid = false,
                unsupportedFeatures = listOf("No real MP4 export backend is available."),
                errorMessage = "No real MP4 export backend is available."
            )
        return backend.validate(settings, project)
    }
}
