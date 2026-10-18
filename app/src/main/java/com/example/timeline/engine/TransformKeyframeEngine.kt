package com.example.timeline.engine

import com.example.timeline.core.transform.TransformInterpolation
import com.example.timeline.core.transform.TransformKeyframe
import com.example.timeline.core.transform.TransformKeyframeTrack
import kotlin.math.abs

object TransformKeyframeEngine {

    fun evaluateKeyframeTrack(
        currentFrame: Long,
        track: TransformKeyframeTrack<Float>,
        defaultValue: Float
    ): Float {
        if (track.keyframes.isEmpty()) return defaultValue
        if (track.keyframes.size == 1) return track.keyframes.first().value

        val frames = LongArray(track.keyframes.size)
        val values = FloatArray(track.keyframes.size)
        val interps = IntArray(track.keyframes.size)
        val lefts = FloatArray(track.keyframes.size)
        val rights = FloatArray(track.keyframes.size)

        for (i in track.keyframes.indices) {
            val kf = track.keyframes[i]
            frames[i] = kf.frame
            values[i] = kf.value
            interps[i] = kf.interpolation.ordinal
            lefts[i] = kf.bezierHandleLeft ?: 0f
            rights[i] = kf.bezierHandleRight ?: 0f
        }

        return NativeTransformEngine.nativeEvaluateKeyframeTrack(
            currentFrame, frames, values, interps, lefts, rights
        )
    }

    fun generateMotionPath(
        startFrame: Long,
        endFrame: Long,
        xTrack: TransformKeyframeTrack<Float>,
        yTrack: TransformKeyframeTrack<Float>
    ): List<Pair<Float, Float>> {
        if (xTrack.keyframes.isEmpty() && yTrack.keyframes.isEmpty()) return emptyList()

        val xFrames = LongArray(xTrack.keyframes.size) { xTrack.keyframes[it].frame }
        val xValues = FloatArray(xTrack.keyframes.size) { xTrack.keyframes[it].value }
        val xInterps = IntArray(xTrack.keyframes.size) { xTrack.keyframes[it].interpolation.ordinal }
        val xLefts = FloatArray(xTrack.keyframes.size) { xTrack.keyframes[it].bezierHandleLeft ?: 0f }
        val xRights = FloatArray(xTrack.keyframes.size) { xTrack.keyframes[it].bezierHandleRight ?: 0f }

        val yFrames = LongArray(yTrack.keyframes.size) { yTrack.keyframes[it].frame }
        val yValues = FloatArray(yTrack.keyframes.size) { yTrack.keyframes[it].value }
        val yInterps = IntArray(yTrack.keyframes.size) { yTrack.keyframes[it].interpolation.ordinal }
        val yLefts = FloatArray(yTrack.keyframes.size) { yTrack.keyframes[it].bezierHandleLeft ?: 0f }
        val yRights = FloatArray(yTrack.keyframes.size) { yTrack.keyframes[it].bezierHandleRight ?: 0f }

        val flatPath = NativeTransformEngine.nativeGenerateMotionPath(
            startFrame, endFrame,
            xFrames, xValues, xInterps, xLefts, xRights,
            yFrames, yValues, yInterps, yLefts, yRights
        )

        val result = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until flatPath.size step 2) {
            result.add(flatPath[i] to flatPath[i + 1])
        }
        return result
    }

    fun findNearestKeyframe(
        currentFrame: Long,
        track: TransformKeyframeTrack<*>,
        thresholdFrames: Long = 5
    ): Long {
        if (track.keyframes.isEmpty()) return -1L
        val frames = LongArray(track.keyframes.size) { track.keyframes[it].frame }
        val nearest = NativeTransformEngine.nativeFindNearestKeyframe(currentFrame, frames)
        if (nearest != -1L && abs(nearest - currentFrame) <= thresholdFrames) {
            return nearest
        }
        return -1L
    }
}
