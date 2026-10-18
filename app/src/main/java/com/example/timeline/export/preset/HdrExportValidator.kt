package com.example.timeline.export.preset

import com.example.timeline.export.model.ExportColorSettings
import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.backend.ExportBackendCapability
import com.example.timeline.export.model.ExportValidationResult

object HdrExportValidator {
    /**
     * Validates if HDR export is supported with the given settings.
     */
    fun validate(
        exportHDR: Boolean,
        colorOutput: ExportColorSettings.ColorOutput,
        codec: ExportCodec,
        mediaCodecCaps: ExportBackendCapability?,
        ffmpegCaps: ExportBackendCapability?
    ): ExportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (exportHDR) {
            if (codec == ExportCodec.H264) {
                errors.add("HDR is not supported with H.264 codec.")
            }
            // Check capabilities
            val mdHdr = mediaCodecCaps?.supportsHDR == true
            val ffHdr = ffmpegCaps?.supportsHDR == true
            
            if (!mdHdr && !ffHdr) {
                errors.add("HDR export is not supported by available backends.")
            }
            
            if (colorOutput == ExportColorSettings.ColorOutput.HLG || colorOutput == ExportColorSettings.ColorOutput.PQ) {
                if (codec == ExportCodec.H265 && !mdHdr && ffHdr) {
                    warnings.add("HDR export with FFmpeg backend may have performance limitations.")
                }
            } else {
                 errors.add("Unsupported HDR color space: \${colorOutput.name}")
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
