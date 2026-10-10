package com.example.model.adjustments.engine

import com.example.model.adjustments.ColorPipelineSettings
import com.example.model.adjustments.VideoAdjustmentParams

class AdjustmentRenderPipeline(
    val colorSettings: ColorPipelineSettings = ColorPipelineSettings()
) {
    private val cpuProcessor = CpuFrameProcessor()
    private val colorEngine = ColorTransformEngine(colorSettings)
    private var gpuRenderer: GpuAdjustmentRenderer? = null

    var useGpu: Boolean = false

    fun setGpuRenderer(renderer: GpuAdjustmentRenderer) {
        gpuRenderer = renderer
        useGpu = true
    }

    fun processFrameCpu(
        frame: AdjustmentPreviewFrame, 
        params: VideoAdjustmentParams, 
        frameTime: Long
    ): AdjustmentPreviewFrame {
        return cpuProcessor.processFrame(frame, params, colorEngine, frameTime)
    }

    fun processFrameGpu(
        textureId: Int, 
        width: Int, 
        height: Int, 
        params: VideoAdjustmentParams, 
        frameTime: Long
    ): Int {
        return gpuRenderer?.renderFrame(textureId, width, height, params, frameTime) ?: textureId
    }
}
