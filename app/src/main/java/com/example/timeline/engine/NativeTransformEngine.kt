package com.example.timeline.engine

import android.util.Log
import com.example.timeline.core.transform.ClipTransform

object NativeTransformEngine {
    private const val TAG = "NativeTransformEngine"
    private var isNativeAvailable = false

    init {
        try {
            System.loadLibrary("transform_engine")
            isNativeAvailable = true
            Log.i(TAG, "Native transform engine loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native transform engine", e)
            isNativeAvailable = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading native transform engine", e)
            isNativeAvailable = false
        }
    }

    fun isAvailable(): Boolean = isNativeAvailable

    external fun nativeCreateTransformEngine(): Long
    external fun nativeReleaseTransformEngine(handle: Long)

    external fun nativeEvaluateTransformMatrix(
        positionX: Float, positionY: Float,
        scaleX: Float, scaleY: Float,
        rotationDegrees: Float,
        anchorX: Float, anchorY: Float,
        flipHorizontal: Boolean, flipVertical: Boolean
    ): FloatArray

    external fun nativeBuildTransformMatrix(paramArray: FloatArray): FloatArray

    external fun nativeEvaluateKeyframeTrack(
        currentFrame: Long,
        keyframeFrames: LongArray,
        keyframeValues: FloatArray,
        keyframeInterpolations: IntArray,
        bezierHandleLefts: FloatArray,
        bezierHandleRights: FloatArray
    ): Float

    external fun nativeGenerateMotionPath(
        startFrame: Long,
        endFrame: Long,
        xKeyframeFrames: LongArray,
        xKeyframeValues: FloatArray,
        xKeyframeInterpolations: IntArray,
        xBezierHandleLefts: FloatArray,
        xBezierHandleRights: FloatArray,
        yKeyframeFrames: LongArray,
        yKeyframeValues: FloatArray,
        yKeyframeInterpolations: IntArray,
        yBezierHandleLefts: FloatArray,
        yBezierHandleRights: FloatArray
    ): FloatArray
    
    external fun nativeFindNearestKeyframe(
        currentFrame: Long,
        keyframeFrames: LongArray
    ): Long

    external fun nativeEvaluateTransformAtFrame(handle: Long, currentFrame: Long): Long
    external fun nativeInterpolateKeyframes(handle: Long, currentFrame: Long): Float
    external fun nativeComputeBoundingBox(handle: Long, currentFrame: Long): FloatArray
    external fun nativeHitTestHandles(handle: Long, x: Float, y: Float): Int

    external fun nativeInterpolateFloat(
        currentFrame: Long, kf1Frame: Long, kf1Value: Float,
        kf2Frame: Long, kf2Value: Float, interpMode: Int
    ): Float

    external fun nativeEvaluateCrop(
        cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
        currentWidth: Float, currentHeight: Float
    ): FloatArray

    fun buildTransformMatrix(transform: ClipTransform, frame: Long = 0L): NativeTransformMatrix {
        val cached = com.example.timeline.engine.performance.NativeTransformCache.get(frame, transform)
        if (cached != null) return cached

        if (!isNativeAvailable) {
            val fallback = NativeTransformMatrix(FloatArray(16) { if (it % 5 == 0) 1f else 0f })
            com.example.timeline.engine.performance.NativeTransformCache.put(frame, transform, fallback)
            return fallback
        }
        val startTime = System.nanoTime()
        val params = floatArrayOf(
            transform.positionX, transform.positionY,
            transform.scaleX, transform.scaleY,
            transform.rotationDegrees,
            transform.anchorPointX, transform.anchorPointY,
            if (transform.flipHorizontal) 1f else 0f,
            if (transform.flipVertical) 1f else 0f
        )
        val matrixVars = nativeBuildTransformMatrix(params)
        val matrix = NativeTransformMatrix(matrixVars ?: FloatArray(16) { if (it % 5 == 0) 1f else 0f })
        
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000f
        com.example.timeline.engine.performance.FrameTimeProfiler.updateNativeTime(elapsedMs)
        com.example.timeline.engine.performance.NativeTransformCache.put(frame, transform, matrix)
        
        return matrix
    }
    
    fun evaluateTransformAtFrame(transform: ClipTransform, currentFrame: Long): ClipTransform {
        // Here we could cache the evaluated object itself if it gets expensive. 
        // For now, we measure the total time native takes if any keyframes are present.
        val startTime = System.nanoTime()
        val evaluated = transform.evaluateTransformAtFrame(currentFrame)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000f
        com.example.timeline.engine.performance.FrameTimeProfiler.updateNativeTime(elapsedMs)
        return evaluated
    }
}
