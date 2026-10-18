package com.example.timeline.engine.preview

import com.example.timeline.core.transform.ClipTransform

data class TransformRenderState(
    val transform: ClipTransform,
    val evaluatedMatrix: com.example.timeline.engine.NativeTransformMatrix,
    val isNativeAvailable: Boolean = false,
    val requiresGpuCompositor: Boolean = false,
    val requiresStillFrameFallback: Boolean = false,
    val capabilityMessage: String? = null
)
