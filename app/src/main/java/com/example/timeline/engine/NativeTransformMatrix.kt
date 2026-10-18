package com.example.timeline.engine

data class NativeTransformMatrix(
    val values: FloatArray // 16 values for 4x4 matrix
) {
    init {
        require(values.size == 16) { "NativeTransformMatrix requires exactly 16 values" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeTransformMatrix

        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }
}
