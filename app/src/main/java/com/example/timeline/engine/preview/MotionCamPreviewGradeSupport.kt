package com.example.timeline.engine.preview

import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.WhiteBalanceTemperature

/**
 * Maps the RAW preview's supported grading parameters to a compact JNI payload. Bayer rendering
 * applies this grade in the native renderer and the playback engine reuses two Bitmap buffers,
 * avoiding per-frame Bitmap allocations and extra full-frame JNI passes. Brightness/contrast/
 * saturation remain in the viewport color filter.
 */
internal object MotionCamPreviewGradeSupport {
    fun hasNativePreviewAdjustments(params: VideoAdjustmentParams): Boolean =
        params.exposureStops != 0f ||
            WhiteBalanceTemperature.resolve(params) != 0f ||
            params.tint != 0f ||
            params.lift.hasOffset() ||
            params.gamma != com.example.model.adjustments.Vector3.ONE ||
            params.gain != com.example.model.adjustments.Vector3.ONE ||
            params.offsetWheel.hasOffset() ||
            params.shadowColorBalance.hasOffset() ||
            params.midtoneColorBalance.hasOffset() ||
            params.highlightColorBalance.hasOffset()

    fun hasNativeGradeChanged(
        previous: VideoAdjustmentParams,
        next: VideoAdjustmentParams
    ): Boolean {
        val previousPayload = nativeGradeParams(previous)
        val nextPayload = nativeGradeParams(next)
        return when {
            previousPayload == null -> nextPayload != null
            nextPayload == null -> true
            else -> !previousPayload.contentEquals(nextPayload)
        }
    }

    /** Order: exposure, temperature/tint, Lift RGB, Gamma RGB, Gain RGB, then Offset RGB. */
    fun nativeGradeParams(params: VideoAdjustmentParams): FloatArray? {
        if (!hasNativePreviewAdjustments(params)) return null
        val lift = params.lift
        val gamma = params.gamma
        val gain = params.gain
        val shadows = params.shadowColorBalance
        val midtones = params.midtoneColorBalance
        val highlights = params.highlightColorBalance
        return floatArrayOf(
            params.exposureStops,
            WhiteBalanceTemperature.resolve(params),
            params.tint,
            lift.x + shadows.x * 0.5f,
            lift.y + shadows.y * 0.5f,
            lift.z + shadows.z * 0.5f,
            gamma.x * (1f - midtones.x * 0.5f),
            gamma.y * (1f - midtones.y * 0.5f),
            gamma.z * (1f - midtones.z * 0.5f),
            gain.x * (1f + highlights.x * 0.5f),
            gain.y * (1f + highlights.y * 0.5f),
            gain.z * (1f + highlights.z * 0.5f),
            params.offsetWheel.x,
            params.offsetWheel.y,
            params.offsetWheel.z
        )
    }

    private fun com.example.model.adjustments.Vector3.hasOffset(): Boolean =
        x != 0f || y != 0f || z != 0f
}
