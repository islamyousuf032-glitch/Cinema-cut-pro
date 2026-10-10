package com.example.timeline.engine.native

import android.util.Log

object NativeTimelineCore {
    private var isNativeAvailable = false

    init {
        try {
            System.loadLibrary("native_timeline_core")
            isNativeAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeTimelineCore", "Failed to load native_timeline_core: ${e.message}")
            isNativeAvailable = false
        }
    }

    fun isAvailable(): Boolean = isNativeAvailable

    external fun nativeFrameToX(frame: Long, pixelsPerFrame: Float): Float
    external fun nativeXToFrame(x: Float, pixelsPerFrame: Float): Long
    external fun nativeCalculateVisibleRange(scrollX: Float, viewportWidth: Int, pixelsPerFrame: Float): LongArray
    external fun nativeLayoutClips(inputData: LongArray, trackHeight: Float, trackPadding: Float, topOffset: Float, pixelsPerFrame: Float, scrollX: Float): FloatArray
    external fun nativeHitTestClip(pointerX: Float, pointerY: Float, clipRectsData: FloatArray): NativeTimelineHitResult
    external fun nativeSnapFrame(inputFrame: Long, targetFrames: LongArray, targetTypes: IntArray, pixelsPerFrame: Float, snapThresholdPixels: Float): NativeTimelineSnapResult
    external fun nativeCalculateThumbnailCells(startFrame: Long, durationFrames: Long, cellWidth: Float, pixelsPerFrame: Float, sourceInFrame: Long): FloatArray
    external fun nativeCalculateRulerTicks(scrollX: Float, viewportWidth: Int, pixelsPerFrame: Float, frameRate: Int): FloatArray

    external fun nativeRippleEdit(inputData: LongArray, targetId: Int, isStart: Boolean, deltaFrames: Long): LongArray
    external fun nativeRollEdit(inputData: LongArray, leftId: Int, rightId: Int, deltaFrames: Long): LongArray
    external fun nativeSlipEdit(inputData: LongArray, deltaFrames: Long): LongArray
    external fun nativeSlideEdit(inputData: LongArray, targetId: Int, deltaFrames: Long): LongArray

    // Kotlin Fallbacks
    fun frameToX(frame: Long, pixelsPerFrame: Float): Float {
        if (isAvailable()) {
            return nativeFrameToX(frame, pixelsPerFrame)
        }
        return frame.toFloat() * pixelsPerFrame
    }

    fun xToFrame(x: Float, pixelsPerFrame: Float): Long {
        if (isAvailable()) {
            return nativeXToFrame(x, pixelsPerFrame)
        }
        if (pixelsPerFrame <= 0) return 0
        return Math.round(x / pixelsPerFrame).toLong()
    }
}
