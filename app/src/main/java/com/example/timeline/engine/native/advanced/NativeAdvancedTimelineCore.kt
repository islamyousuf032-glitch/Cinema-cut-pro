package com.example.timeline.engine.native.advanced

import android.util.Log

object NativeAdvancedTimelineCore {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("native_advanced_timeline_core")
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeAdvancedTimeline", "Failed to load native_advanced_timeline_core library", e)
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded

    external fun nativeFrameToX(frame: Long, pixelsPerFrame: Float): Float
    external fun nativeXToFrame(x: Float, pixelsPerFrame: Float): Long
    external fun nativeCalculateVisibleFrameRange(scrollX: Float, viewportWidth: Int, pixelsPerFrame: Float): LongArray
    external fun nativeCalculatePlayheadX(currentFrame: Long, scrollX: Float, pixelsPerFrame: Float): Float
    external fun nativeLayoutClipRects(inputData: LongArray, trackHeight: Float, trackPadding: Float, topOffset: Float, pixelsPerFrame: Float, scrollX: Float): FloatArray
    external fun nativeLayoutTrackRects(numTracks: Int, viewportWidth: Float, trackHeight: Float, trackPadding: Float, topOffset: Float): FloatArray
    external fun nativeCalculateRulerTicks(scrollX: Float, viewportWidth: Int, pixelsPerFrame: Float, frameRate: Int): FloatArray
    external fun nativeHitTestClip(pointerX: Float, pointerY: Float, clipRectsData: FloatArray): FloatArray
    external fun nativeSnapFrame(inputFrame: Long, snapTargets: LongArray, thresholdFrames: Long): Long

    // Kotlin Fallbacks
    fun frameToX(frame: Long, pixelsPerFrame: Float): Float {
        if (isAvailable()) {
            return nativeFrameToX(frame, pixelsPerFrame)
        }
        Log.w("NativeAdvancedTimeline", "Using Kotlin fallback for frameToX")
        return frame * pixelsPerFrame
    }

    fun xToFrame(x: Float, pixelsPerFrame: Float): Long {
        if (isAvailable()) {
            return nativeXToFrame(x, pixelsPerFrame)
        }
        Log.w("NativeAdvancedTimeline", "Using Kotlin fallback for xToFrame")
        if (pixelsPerFrame <= 0f) return 0L
        return (x / pixelsPerFrame).toLong()
    }
}
