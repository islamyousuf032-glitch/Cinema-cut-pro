package com.example.timeline.export.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportFrameRate(
    val numerator: Int,
    val denominator: Int
) {
    val floatValue: Float get() = numerator.toFloat() / denominator.toFloat()
    
    companion object {
        val FPS_23_976 = ExportFrameRate(24000, 1001)
        val FPS_24 = ExportFrameRate(24, 1)
        val FPS_25 = ExportFrameRate(25, 1)
        val FPS_29_97 = ExportFrameRate(30000, 1001)
        val FPS_30 = ExportFrameRate(30, 1)
        val FPS_50 = ExportFrameRate(50, 1)
        val FPS_59_94 = ExportFrameRate(60000, 1001)
        val FPS_60 = ExportFrameRate(60, 1)
        val FPS_120 = ExportFrameRate(120, 1)
    }
}
