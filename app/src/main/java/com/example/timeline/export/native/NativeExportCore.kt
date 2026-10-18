package com.example.timeline.export.native

import java.nio.ByteBuffer

object NativeExportCore {
    private var isAvailable = false

    init {
        try {
            System.loadLibrary("export_core")
            isAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            isAvailable = false
        }
    }

    fun isAvailable(): Boolean = isAvailable

    external fun nativeCreateExportCore(): Boolean
    external fun nativeReleaseExportCore()
    external fun nativeEvaluateFramePlan(frameIndex: Long): Boolean
    external fun nativeApplyTransform(buffer: ByteBuffer, width: Int, height: Int, scaleX: Float, scaleY: Float, rotation: Float, posX: Float, posY: Float, opacity: Float)
    external fun nativeApplyColor(buffer: ByteBuffer, width: Int, height: Int, exposure: Float, brightness: Float, contrast: Float, saturation: Float)
    external fun nativeApplyClipAdjustments(buffer: ByteBuffer, width: Int, height: Int, parameters: FloatArray)
    external fun nativeApplyLut(buffer: ByteBuffer, width: Int, height: Int, lutBuffer: ByteBuffer, lutSize: Int)
    external fun nativeCompositeFrame(bgBuffer: ByteBuffer, fgBuffer: ByteBuffer, width: Int, height: Int, blendMode: Int)
    external fun nativeBlendWatermarkYuv(yuvBuffer: ByteBuffer, watermarkRgbaBuffer: ByteBuffer, width: Int, height: Int)
    external fun nativeConvertRgbaToYuv420(rgbaBuffer: ByteBuffer, yuvBuffer: ByteBuffer, width: Int, height: Int)
    external fun nativeMixAudio(destBuffer: ByteBuffer, srcBuffer: ByteBuffer, numSamples: Int, volume: Float)
    external fun nativeGetStats(): IntArray
    
    fun getStats(): NativeExportStats {
        if (!isAvailable) return NativeExportStats(0, 0, 0)
        val arr = nativeGetStats()
        if (arr.size >= 3) {
            return NativeExportStats(arr[0], arr[1], arr[2])
        }
        return NativeExportStats(0, 0, 0)
    }
}
