package com.example.model.adjustments.engine

class AdjustmentPreviewFrame(
    val width: Int,
    val height: Int,
    val pixels: FloatArray // RGBA normalized 0.0 - 1.0
) {
    fun clone(): AdjustmentPreviewFrame {
        return AdjustmentPreviewFrame(width, height, pixels.copyOf())
    }
}
