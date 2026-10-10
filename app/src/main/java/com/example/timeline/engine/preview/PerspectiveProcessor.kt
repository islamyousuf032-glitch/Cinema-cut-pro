package com.example.timeline.engine.preview

import androidx.compose.ui.graphics.Matrix
import com.example.timeline.core.transform.PerspectiveParams
import com.example.timeline.engine.NativeTransformMatrix

object PerspectiveProcessor {
    // We already have a C++ backend that computes the 4x4 matrix for us.
    // This processor converts it to Compose constraints.
    fun toComposeMatrix(nativeMatrix: NativeTransformMatrix): Matrix {
        return Matrix(nativeMatrix.values.clone()) // Ensure it's a copy
    }

    // In a pure still-frame fallback or an environment where we need custom bounds overlay:
    fun calculateBounds(params: PerspectiveParams, width: Float, height: Float): FloatArray {
        if (!params.enabled) {
            return floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
        }
        return floatArrayOf(
            params.topLeftX * width, params.topLeftY * height,
            params.topRightX * width, params.topRightY * height,
            params.bottomRightX * width, params.bottomRightY * height,
            params.bottomLeftX * width, params.bottomLeftY * height
        )
    }
}
