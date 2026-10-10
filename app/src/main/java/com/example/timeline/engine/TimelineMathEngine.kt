package com.example.timeline.engine

object TimelineMathEngine {
    init {
        try {
            System.loadLibrary("timeline_engine")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun nativeFrameToPixel(frame: Long, pixelsPerFrame: Float): Float
    external fun nativePixelToFrame(pixel: Float, pixelsPerFrame: Float): Long
    external fun nativeIsFrameVisible(frame: Long, startVisibleFrame: Long, endVisibleFrame: Long): Boolean
    external fun nativeCalculateSnapPoint(dragFrame: Long, snapPoints: LongArray, thresholdFrames: Long): Long

    fun frameToPixel(frame: Long, pixelsPerFrame: Float): Float = nativeFrameToPixel(frame, pixelsPerFrame)
    fun pixelToFrame(pixel: Float, pixelsPerFrame: Float): Long = nativePixelToFrame(pixel, pixelsPerFrame)
    fun isFrameVisible(frame: Long, startVisibleFrame: Long, endVisibleFrame: Long): Boolean = nativeIsFrameVisible(frame, startVisibleFrame, endVisibleFrame)
    fun calculateSnapPoint(dragFrame: Long, snapPoints: LongArray, thresholdFrames: Long): Long = nativeCalculateSnapPoint(dragFrame, snapPoints, thresholdFrames)
}
