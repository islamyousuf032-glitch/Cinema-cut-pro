package com.example.model.adjustments.engine

enum class RenderQuality {
    FAST_PREVIEW,
    BALANCED,
    HIGH_QUALITY,
    EXPORT_QUALITY
}

data class RenderQualitySettings(
    val quality: RenderQuality = RenderQuality.BALANCED,
    val use16BitFloat: Boolean = true,
    val enableMultiPass: Boolean = false,
    val downsampleFactor: Int = 1
) {
    companion object {
        fun defaultFor(quality: RenderQuality): RenderQualitySettings {
            return when (quality) {
                RenderQuality.FAST_PREVIEW -> RenderQualitySettings(quality, false, false, 2)
                RenderQuality.BALANCED -> RenderQualitySettings(quality, true, false, 1)
                RenderQuality.HIGH_QUALITY -> RenderQualitySettings(quality, true, true, 1)
                RenderQuality.EXPORT_QUALITY -> RenderQualitySettings(quality, true, true, 1)
            }
        }
    }
}
