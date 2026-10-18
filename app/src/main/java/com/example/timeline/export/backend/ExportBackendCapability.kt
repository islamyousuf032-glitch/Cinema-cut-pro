package com.example.timeline.export.backend

import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer

data class ExportBackendCapability(
    val backendAvailable: Boolean,
    val hardwareAccelerated: Boolean,
    val supportedCodecs: List<ExportCodec>,
    val supportedContainers: List<ExportContainer>,
    val maxResolutionWidth: Int,
    val maxResolutionHeight: Int,
    val maxFps: Float,
    val supportsHDR: Boolean,
    val supportsAlpha: Boolean,
    val supportsAudioMix: Boolean,
    val supportsVideoComposition: Boolean,
    val supportsProRes: Boolean,
    val supportsAV1: Boolean,
    val supports10Bit: Boolean,
    val supportsBatch: Boolean,
    val supportsCancel: Boolean
)
