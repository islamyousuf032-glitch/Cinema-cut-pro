package com.example.timeline.export.preset

import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import com.example.timeline.export.backend.ExportBackendCapability
import com.example.timeline.export.model.ExportValidationResult

object ExportCodecValidator {
    /**
     * Validates if the selected codec and container combination is supported.
     * Takes available capabilities from backends.
     */
    fun validate(
        codec: ExportCodec,
        container: ExportContainer,
        mediaCodecCaps: ExportBackendCapability?,
        ffmpegCaps: ExportBackendCapability?
    ): ExportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val hasFfmpeg = ffmpegCaps?.backendAvailable == true

        when (codec) {
            ExportCodec.H264 -> {
                if (container == ExportContainer.MP4) {
                    val mediaCodecSupportsH264 = mediaCodecCaps?.supportedCodecs?.contains(ExportCodec.H264) == true
                    if (!mediaCodecSupportsH264 && !hasFfmpeg) {
                        errors.add("Required backend not found for H.264 MP4 export.")
                    }
                } else if (container == ExportContainer.MKV || container == ExportContainer.MOV) {
                    if (!hasFfmpeg) {
                        errors.add("Requires FFmpeg backend for \${container.name} container.")
                    }
                } else {
                    errors.add("Container \${container.name} not supported with H.264.")
                }
            }
            ExportCodec.H265 -> {
                if (container == ExportContainer.MP4) {
                    if (mediaCodecCaps?.supportedCodecs?.contains(ExportCodec.H265) != true && !hasFfmpeg) {
                        errors.add("Requires hardware encoder for H.265 or FFmpeg backend.")
                    }
                } else if (container == ExportContainer.MKV || container == ExportContainer.MOV) {
                    if (!hasFfmpeg) {
                        errors.add("Requires FFmpeg backend for \${container.name} container.")
                    }
                } else {
                    errors.add("Container \${container.name} not supported with H.265.")
                }
            }
            ExportCodec.AV1 -> {
                if (container == ExportContainer.MP4 || container == ExportContainer.MKV) {
                    val mediaCodecSupportsAv1 = mediaCodecCaps?.supportedCodecs?.contains(ExportCodec.AV1) == true
                    val ffmpegSupportsAv1 = ffmpegCaps?.supportedCodecs?.contains(ExportCodec.AV1) == true
                    if (!mediaCodecSupportsAv1 && !ffmpegSupportsAv1) {
                        errors.add("AV1 codec is not supported by device or backend.")
                    }
                } else {
                    errors.add("Container \${container.name} not supported with AV1.")
                }
            }
            ExportCodec.PRORES -> {
                if (!hasFfmpeg) {
                    errors.add("Requires FFmpeg backend for ProRes export.")
                }
                if (container != ExportContainer.MOV) {
                    errors.add("ProRes requires MOV container.")
                }
            }
            else -> {
                // For other codecs like IMAGE_SEQUENCE
                if (codec == ExportCodec.IMAGE_SEQUENCE) {
                     // Image sequence might not need a typical container
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
