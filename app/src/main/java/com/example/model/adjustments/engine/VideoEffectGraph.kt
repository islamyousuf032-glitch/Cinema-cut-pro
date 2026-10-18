package com.example.model.adjustments.engine

import android.content.Context
import androidx.media3.common.Effect
import com.example.model.adjustments.AdjustmentStack

class VideoEffectGraph(
    private val context: Context,
    private val frameProcessor: GpuVideoFrameProcessor = Media3GpuVideoFrameProcessor()
) {
    init {
        frameProcessor.setup(context)
    }

    fun updateFromStack(stack: AdjustmentStack, presentationTimeUs: Long) {
        if (stack.enabled) {
            val params = stack.evaluateParamsAtFrame(presentationTimeUs)
            frameProcessor.setParams(params)
        } else {
            frameProcessor.setParams(com.example.model.adjustments.VideoAdjustmentParams.default())
        }
    }
    
    fun setParams(params: com.example.model.adjustments.VideoAdjustmentParams) {
        frameProcessor.setParams(params)
    }

    fun getEffects(): List<Effect> {
        return frameProcessor.getMedia3Effects()
    }

    fun release() {
        frameProcessor.release()
    }
}
