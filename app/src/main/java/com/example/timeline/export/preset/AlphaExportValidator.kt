package com.example.timeline.export.preset

import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import com.example.timeline.export.model.ExportValidationResult
import com.example.timeline.export.backend.ExportBackendCapability

object AlphaExportValidator {
    /**
     * Validates if export with Alpha channel (transparency) is supported.
     */
    fun validate(
        hasAlpha: Boolean,
        codec: ExportCodec,
        container: ExportContainer,
        ffmpegCaps: ExportBackendCapability?
    ): ExportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (hasAlpha) {
            when (codec) {
                ExportCodec.PRORES -> {
                    if (ffmpegCaps?.backendAvailable != true) {
                         errors.add("Requires FFmpeg backend for ProRes with Alpha.")
                    }
                }
                ExportCodec.H265 -> {
                    if (container != ExportContainer.MP4 && container != ExportContainer.MOV) {
                        errors.add("HEVC with Alpha requires MP4 or MOV container.")
                    }
                    warnings.add("HEVC with Alpha may only be supported on specific Apple platforms or modern browsers.")
                }
                ExportCodec.AV1 -> {
                     warnings.add("AV1 Alpha channel support is experimental.")
                }
                else -> {
                    errors.add("Alpha channel export is not supported with \${codec.name} codec.")
                }
            }
        }

        return ExportValidationResult(
            isValid = errors.isEmpty(),
            unsupportedFeatures = errors,
            warnings = warnings,
            errorMessage = errors.firstOrNull()
        )
    }
}
