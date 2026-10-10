package com.example.timeline.engine.preview

import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.engine.NativeTransformEngine

object PreviewTransformAdapter {
    fun adaptTransformForEngine(
        transform: ClipTransform,
        engineType: PreviewEngineType,
        playheadFrame: Long
    ): TransformRenderState {
        val evaluated = NativeTransformEngine.evaluateTransformAtFrame(transform, playheadFrame)
        val matrix = NativeTransformEngine.buildTransformMatrix(evaluated)
        
        val hasPerspective = evaluated.perspectiveParams.enabled
        val hasCrop = evaluated.cropParams.cropLeft > 0f || evaluated.cropParams.cropRight > 0f || 
                      evaluated.cropParams.cropTop > 0f || evaluated.cropParams.cropBottom > 0f
        
        var requiresGPU = false
        var requiresStill = false
        var message: String? = null

        when (engineType) {
            PreviewEngineType.VLC_NATIVE, PreviewEngineType.BROWSER, PreviewEngineType.MEDIA3_FALLBACK -> {
                if (hasPerspective) {
                    message = "Perspective preview requires GPU compositor or still-frame preview"
                    requiresStill = true
                } else if (hasCrop) {
                    message = "Crop preview may be limited without GPU compositor"
                }
            }
            PreviewEngineType.STILL_FRAME -> {
                // Still frame can do everything using C++ rasterizer or Canvas
            }
            else -> {}
        }
        
        return TransformRenderState(
            transform = evaluated,
            evaluatedMatrix = matrix,
            isNativeAvailable = NativeTransformEngine.isAvailable(),
            requiresGpuCompositor = requiresGPU,
            requiresStillFrameFallback = requiresStill,
            capabilityMessage = message
        )
    }
}
