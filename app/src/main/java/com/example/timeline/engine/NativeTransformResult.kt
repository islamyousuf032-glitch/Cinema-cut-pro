package com.example.timeline.engine

data class NativeTransformResult(
    val matrix4x4: FloatArray,
    val cropRect: FloatArray,
    val isVisible: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeTransformResult

        if (!matrix4x4.contentEquals(other.matrix4x4)) return false
        if (!cropRect.contentEquals(other.cropRect)) return false
        if (isVisible != other.isVisible) return false

        return true
    }

    override fun hashCode(): Int {
        var result = matrix4x4.contentHashCode()
        result = 31 * result + cropRect.contentHashCode()
        result = 31 * result + isVisible.hashCode()
        return result
    }
}
