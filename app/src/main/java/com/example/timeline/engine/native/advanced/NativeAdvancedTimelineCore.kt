package com.example.timeline.engine.native.advanced

import android.util.Log

object NativeAdvancedTimelineCore {
    @Volatile private var isNativeLoaded = false
    @Volatile private var nativeFailure: String? = null

    init {
        try {
            System.loadLibrary("native_advanced_timeline_core")
            isNativeLoaded = true
        } catch (e: LinkageError) {
            disableNative("native library could not be loaded", e)
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded && nativeFailure == null

    private fun disableNative(reason: String, error: Throwable) {
        nativeFailure = reason
        isNativeLoaded = false
        if (com.example.BuildConfig.DEBUG) {
            Log.w("NativeAdvancedTimeline", "Using Kotlin fallback because $reason", error)
        }
    }

    private inline fun <T> tryNativeCall(name: String, call: () -> T): T? {
        if (!isAvailable()) return null
        return try {
            call()
        } catch (error: LinkageError) {
            disableNative("native call $name is unavailable", error)
            null
        } catch (error: RuntimeException) {
            disableNative("native call $name failed", error)
            null
        }
    }

    fun layoutClipRectsOrNull(
        inputData: LongArray,
        trackHeight: Float,
        trackPadding: Float,
        topOffset: Float,
        pixelsPerFrame: Float,
        scrollX: Float
    ): FloatArray? = tryNativeCall("layoutClipRects") {
        nativeLayoutClipRects(inputData, trackHeight, trackPadding, topOffset, pixelsPerFrame, scrollX)
    }

    fun calculateRulerTicksOrNull(
        scrollX: Float,
        viewportWidth: Int,
        pixelsPerFrame: Float,
        frameRate: Int
    ): FloatArray? = tryNativeCall("calculateRulerTicks") {
        nativeCalculateRulerTicks(scrollX, viewportWidth, pixelsPerFrame, frameRate)
    }

    fun hitTestClipOrNull(pointerX: Float, pointerY: Float, clipRectsData: FloatArray): FloatArray? =
        tryNativeCall("hitTestClip") {
            nativeHitTestClip(pointerX, pointerY, clipRectsData)
        }

    fun snapFrameOrNull(inputFrame: Long, snapTargets: LongArray, thresholdFrames: Long): Long? =
        tryNativeCall("snapFrame") {
            nativeSnapFrame(inputFrame, snapTargets, thresholdFrames)
        }

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
        tryNativeCall("frameToX") { nativeFrameToX(frame, pixelsPerFrame) }?.let { return it }
        return frame * pixelsPerFrame
    }

    fun xToFrame(x: Float, pixelsPerFrame: Float): Long {
        tryNativeCall("xToFrame") { nativeXToFrame(x, pixelsPerFrame) }?.let { return it }
        if (pixelsPerFrame <= 0f) return 0L
        return (x / pixelsPerFrame).toLong()
    }
}
