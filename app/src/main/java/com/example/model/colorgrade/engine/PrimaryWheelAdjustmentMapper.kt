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
}
