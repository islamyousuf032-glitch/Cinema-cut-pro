package com.example.timeline.export.backend

import androidx.media3.common.Effect
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.engine.VideoAdjustmentEffect

/** The export pipeline uses the exact GLSL effect and uniform mapping used by Media3 preview. */
internal fun createMedia3ClipAdjustmentEffect(params: VideoAdjustmentParams): Effect =
    VideoAdjustmentEffect(params)
