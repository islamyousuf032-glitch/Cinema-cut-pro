package com.example.model.adjustments.engine

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.model.adjustments.VideoAdjustmentParams

class VideoAdjustmentEffect(
    var currentParams: VideoAdjustmentParams = VideoAdjustmentParams.default()
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AdjustmentShaderProgram(context, useHdr) { currentParams }
    }
}
