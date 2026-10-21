package com.example.timeline.engine.preview

import com.example.model.adjustments.VideoAdjustmentParams

/**
 * The MotionCam RAW Bitmap preview has a small native CPU grading path. Brightness,
 * contrast, and saturation continue through the viewport's Compose color matrix so the
 * same controls are not applied twice.
 */
internal object MotionCamPreviewGradeSupport {
    fun hasNativePreviewAdjustments(params: VideoAdjustmentParams): Boolean =
        params.exposureStops != 0f ||
            params.temperature != 0f ||
            params.tint != 0f ||
            params.lift.hasOffset() ||
            params.gamma != com.example.model.adjustments.Vector3.ONE ||
            params.gain != com.example.model.adjustments.Vector3.ONE ||
            params.offsetWheel.hasOffset() ||
            params.shadowColorBalance.hasOffset() ||
            params.midtoneColorBalance.hasOffset() ||
            params.highlightColorBalance.hasOffset()

    fun paramsForNativeBitmapProcessing(params: VideoAdjustmentParams): VideoAdjustmentParams =
        params.copy(
            brightness = 0f,
            contrast = 0f,
            saturation = 1f
        )

    private fun com.example.model.adjustments.Vector3.hasOffset(): Boolean =
        x != 0f || y != 0f || z != 0f
}
