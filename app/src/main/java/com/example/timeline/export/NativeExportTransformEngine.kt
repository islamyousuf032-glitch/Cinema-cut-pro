package com.example.timeline.export

object NativeExportTransformEngine {
    init {
        try {
            System.loadLibrary("timeline_engine")
        } catch (e: UnsatisfiedLinkError) {
            // Error loading native library
        }
    }

    external fun nativeApplyTransformRgba8888(
        outPixels: ByteArray,
        inPixels: ByteArray,
        width: Int,
        height: Int,
        px: Float, py: Float, rot: Float, sx: Float, sy: Float,
        anchorX: Float, anchorY: Float,
        cropL: Float, cropT: Float, cropR: Float, cropB: Float,
        opacity: Float
    )
}
