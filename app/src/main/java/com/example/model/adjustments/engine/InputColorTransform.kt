package com.example.model.adjustments.engine

import com.example.model.adjustments.ColorSpaceProfile

interface InputColorTransform {
    val inputColorSpace: ColorSpaceProfile
    val outputColorSpace: ColorSpaceProfile
    
    /**
     * Non-destructive transformation matrix or 3D LUT
     * converting from inputColorSpace to working profile (e.g. Rec.2020 Linear).
     * Implementation should be precision-aware for 10-bit+.
     */
    fun getTransformMatrix(): FloatArray
}

class StandardColorSpaceTransform(
    override val inputColorSpace: ColorSpaceProfile,
    override val outputColorSpace: ColorSpaceProfile
) : InputColorTransform {
    override fun getTransformMatrix(): FloatArray {
        // Mock identity matrix or actual color space conversion matrix
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}
