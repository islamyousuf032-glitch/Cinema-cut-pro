package com.example.timeline.export.performance

data class NativeExportStats(
    var nativeRenderTimeMs: Long = 0L,
    var nativeColorTimeMs: Long = 0L,
    var nativeTransformTimeMs: Long = 0L,
    var nativeCompositeTimeMs: Long = 0L,
    var memoryAllocatedBytes: Long = 0L
) {
    fun reset() {
        nativeRenderTimeMs = 0L
        nativeColorTimeMs = 0L
        nativeTransformTimeMs = 0L
        nativeCompositeTimeMs = 0L
        memoryAllocatedBytes = 0L
    }
}
