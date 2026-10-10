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
    private var sharpenEffect: SharpenEffect? = null
    
    override fun setup(context: Context) {
        if (colorEffect == null) {
            val params = VideoAdjustmentParams.default()
            colorEffect = VideoAdjustmentEffect(params)
            sharpenEffect = SharpenEffect(params)
        }
    }
    
    override fun release() {
        colorEffect = null
        sharpenEffect = null
    }
    
    override fun getMedia3Effects(): List<Effect> {
        val effects = mutableListOf<Effect>()
        colorEffect?.let { effects.add(it) }
        sharpenEffect?.let { effects.add(it) }
        return effects
    }
    
    override fun setParams(params: VideoAdjustmentParams) {
        colorEffect?.currentParams = params
        sharpenEffect?.currentParams = params
    }
}
