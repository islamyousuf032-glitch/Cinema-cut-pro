package com.example.model.adjustments.engine

import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.ColorPipelineSettings
import com.example.model.adjustments.VideoAdjustmentParams

class VideoAdjustmentEngine(
    private val pipeline: AdjustmentRenderPipeline = AdjustmentRenderPipeline(ColorPipelineSettings())
) {
    fun evaluateParamsForFrame(stack: AdjustmentStack, frameTime: Long): VideoAdjustmentParams {
        if (!stack.enabled) return VideoAdjustmentParams.default()
        return stack.evaluateParamsAtFrame(frameTime)
    }

    fun processTestFrame(frame: AdjustmentPreviewFrame, stack: AdjustmentStack, frameTime: Long): AdjustmentPreviewFrame {
        if (!stack.enabled) return frame
        val params = evaluateParamsForFrame(stack, frameTime)
        return pipeline.processFrameCpu(frame, params, frameTime)
    }

    fun renderVideoFrame(textureId: Int, width: Int, height: Int, stack: AdjustmentStack, frameTime: Long): Int {
        if (!stack.enabled) return textureId
        val params = evaluateParamsForFrame(stack, frameTime)
        return pipeline.processFrameGpu(textureId, width, height, params, frameTime)
    }
}
