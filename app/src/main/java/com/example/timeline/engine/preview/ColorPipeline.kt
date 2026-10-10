package com.example.timeline.engine.preview

import com.example.timeline.core.VideoAdjustments

interface ColorPipeline {
    fun setAdjustments(adjustments: VideoAdjustments)
}

// CPU reference processor for still frame testing
class CpuColorPipeline : ColorPipeline {
    override fun setAdjustments(adjustments: VideoAdjustments) {
        // CPU fallback dummy
    }
}
