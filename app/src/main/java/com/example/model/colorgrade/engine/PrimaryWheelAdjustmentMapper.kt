package com.example.model.colorgrade.engine

import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.Vector3
import com.example.model.colorgrade.ColorGradeParams

/**
 * Maps the editor's primary Lift/Gamma/Gain/Offset wheels onto the uniforms shared by
 * Media3 preview and Media3 Transformer export. Luma is added equally to RGB before range
 * limiting so every wheel's luminance control has an effect in the same shader path.
 */
object PrimaryWheelAdjustmentMapper {
    fun apply(base: VideoAdjustmentParams, grade: ColorGradeParams): VideoAdjustmentParams {
        fun wheelVector(r: Float, g: Float, b: Float, luma: Float, minimum: Float, maximum: Float) =
            Vector3(
                (r + luma).coerceIn(minimum, maximum),
                (g + luma).coerceIn(minimum, maximum),
                (b + luma).coerceIn(minimum, maximum)
            )

        return base.copy(
            lift = wheelVector(
                grade.lift.r, grade.lift.g, grade.lift.b, grade.lift.luma,
                minimum = -1f, maximum = 1f
            ),
            gamma = wheelVector(
                grade.gamma.r + 1f, grade.gamma.g + 1f, grade.gamma.b + 1f, grade.gamma.luma,
                minimum = 0.1f, maximum = 2f
            ),
            gain = wheelVector(
                grade.gain.r + 1f, grade.gain.g + 1f, grade.gain.b + 1f, grade.gain.luma,
                minimum = 0f, maximum = 2f
            ),
            offsetWheel = wheelVector(
                grade.offset.r, grade.offset.g, grade.offset.b, grade.offset.luma,
                minimum = -1f, maximum = 1f
            ),
            exposureStops = grade.exposure,
            contrast = grade.contrast,
            saturation = grade.saturation,
            vibrance = grade.vibrance,
            temperature = grade.temperature,
            tint = grade.tint
        )
    }

    /**
     * Applies only primary-grade fields that actually changed. Other grade controls (such as
     * curves) must not reset unrelated values already stored in the shared adjustment params.
     * If an older project has a non-neutral grade but neutral mirrored params, this also fills
     * in the missing mirrored values once.
     */
    fun applyChanges(
        base: VideoAdjustmentParams,
        previousGrade: ColorGradeParams,
        newGrade: ColorGradeParams
    ): VideoAdjustmentParams {
        val defaults = VideoAdjustmentParams.default()
        val previous = apply(defaults, previousGrade)
        val next = apply(defaults, newGrade)

        fun <T> choose(existing: T, previousValue: T, newValue: T, neutralValue: T): T = when {
            previousValue != newValue -> newValue
            existing == neutralValue && newValue != neutralValue -> newValue
            else -> existing
        }

        return base.copy(
            lift = choose(base.lift, previous.lift, next.lift, defaults.lift),
            gamma = choose(base.gamma, previous.gamma, next.gamma, defaults.gamma),
            gain = choose(base.gain, previous.gain, next.gain, defaults.gain),
            offsetWheel = choose(base.offsetWheel, previous.offsetWheel, next.offsetWheel, defaults.offsetWheel),
            exposureStops = choose(base.exposureStops, previous.exposureStops, next.exposureStops, defaults.exposureStops),
            contrast = choose(base.contrast, previous.contrast, next.contrast, defaults.contrast),
            saturation = choose(base.saturation, previous.saturation, next.saturation, defaults.saturation),
            vibrance = choose(base.vibrance, previous.vibrance, next.vibrance, defaults.vibrance),
            temperature = choose(base.temperature, previous.temperature, next.temperature, defaults.temperature),
            tint = choose(base.tint, previous.tint, next.tint, defaults.tint)
        )
    }
}
