package com.example.timeline.export.backend

data class ExportBackendDiagnostics(
    val mediaCodecAvailable: Boolean,
    val ffmpegNativeAvailable: Boolean,
    val maxAvailableResolutionWidth: Int,
    val maxAvailableResolutionHeight: Int,
    val hardwareAv1Supported: Boolean,
    val availableCodecs: List<String>,
    val mediaCodecCapabilities: ExportBackendCapability? = null,
    val ffmpegCapabilities: ExportBackendCapability? = null
)
