package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class FrameRate(
    val numerator: Int,
    val denominator: Int
) {
    val fpsAsFloat: Float
        get() = numerator.toFloat() / denominator.toFloat()

    companion object {
        val FPS_23_976 = FrameRate(24000, 1001)
        val FPS_24 = FrameRate(24, 1)
        val FPS_25 = FrameRate(25, 1)
        val FPS_29_97 = FrameRate(30000, 1001)
        val FPS_30 = FrameRate(30, 1)
        val FPS_50 = FrameRate(50, 1)
        val FPS_59_94 = FrameRate(60000, 1001)
        val FPS_60 = FrameRate(60, 1)
    }
}
