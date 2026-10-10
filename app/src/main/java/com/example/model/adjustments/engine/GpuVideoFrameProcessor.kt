package com.example.model.adjustments.engine

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.effect.GlEffect
import com.example.model.adjustments.VideoAdjustmentParams

interface GpuVideoFrameProcessor {
    fun setup(context: Context)
    fun release()
    fun getMedia3Effects(): List<Effect>
    fun setParams(params: VideoAdjustmentParams)
}

class Media3GpuVideoFrameProcessor : GpuVideoFrameProcessor {
    private var colorEffect: VideoAdjustmentEffect? = null

    override fun setup(context: Context) {
        if (colorEffect == null) {
            colorEffect = VideoAdjustmentEffect(VideoAdjustmentParams.default())
        }
    }

    override fun release() {
        colorEffect = null
    }

    override fun getMedia3Effects(): List<Effect> = listOfNotNull(colorEffect)

    override fun setParams(params: VideoAdjustmentParams) {
        colorEffect?.currentParams = params
    }
}
