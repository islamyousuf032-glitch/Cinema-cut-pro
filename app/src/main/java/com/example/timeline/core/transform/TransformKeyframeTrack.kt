package com.example.timeline.core.transform

import kotlinx.serialization.Serializable
import com.example.timeline.engine.NativeTransformEngine

@Serializable
data class TransformKeyframeTrack<T>(
    val parameterId: String,
    val keyframes: List<TransformKeyframe<T>> = emptyList()
) {
    fun hasKeyframes(): Boolean = keyframes.isNotEmpty()

    fun addKeyframe(keyframe: TransformKeyframe<T>): TransformKeyframeTrack<T> {
        val existingIndex = keyframes.indexOfFirst { it.frame == keyframe.frame }
        if (existingIndex != -1) {
            val updated = keyframes.toMutableList()
            updated[existingIndex] = keyframe
            return copy(keyframes = updated.sortedBy { it.frame })
        }
        return copy(keyframes = (keyframes + keyframe).sortedBy { it.frame })
    }

    fun updateKeyframe(keyframe: TransformKeyframe<T>): TransformKeyframeTrack<T> {
        val index = keyframes.indexOfFirst { it.id == keyframe.id }
        if (index == -1) return this
        val updated = keyframes.toMutableList()
        updated[index] = keyframe
        return copy(keyframes = updated.sortedBy { it.frame })
    }

    fun deleteKeyframe(id: String): TransformKeyframeTrack<T> {
        return copy(keyframes = keyframes.filterNot { it.id == id })
    }
    
    fun evaluate(frame: Long, defaultValue: T): T {
        if (keyframes.isEmpty()) return defaultValue
        if (keyframes.size == 1) return keyframes.first().value
        
        val first = keyframes.first()
        if (frame <= first.frame) return first.value
        
        val last = keyframes.last()
        if (frame >= last.frame) return last.value
        
        for (i in 0 until keyframes.size - 1) {
            val kf1 = keyframes[i]
            val kf2 = keyframes[i+1]
            if (frame >= kf1.frame && frame <= kf2.frame) {
                if (kf1.interpolation == TransformInterpolation.HOLD) {
                    return kf1.value
                }
                
                // If native engine is not available, we could do basic linear here.
                // But let's rely on NativeTransformEngine since it's the goal.
                if (!NativeTransformEngine.isAvailable()) {
                    // Fallback basic linear
                    val t = (frame - kf1.frame).toFloat() / (kf2.frame - kf1.frame).toFloat()
                    return interpolateValue(kf1.value, kf2.value, t)
                }

                return interpolateWithNative(frame, kf1, kf2)
            }
        }
        return defaultValue
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun interpolateWithNative(currentFrame: Long, kf1: TransformKeyframe<T>, kf2: TransformKeyframe<T>): T {
        val interpMode = kf1.interpolation.ordinal
        val v1 = kf1.value
        val v2 = kf2.value
        
        if (v1 is Float && v2 is Float) {
            val result = NativeTransformEngine.nativeInterpolateFloat(
                currentFrame, kf1.frame, v1, kf2.frame, v2, interpMode
            )
            return result as T
        } else if (v1 is PairFloat && v2 is PairFloat) {
            val first = NativeTransformEngine.nativeInterpolateFloat(
                currentFrame, kf1.frame, v1.first, kf2.frame, v2.first, interpMode
            )
            val second = NativeTransformEngine.nativeInterpolateFloat(
                currentFrame, kf1.frame, v1.second, kf2.frame, v2.second, interpMode
            )
            return PairFloat(first, second) as T
        } else if (v1 is CropParams && v2 is CropParams) {
            val cleft = NativeTransformEngine.nativeInterpolateFloat(currentFrame, kf1.frame, v1.cropLeft, kf2.frame, v2.cropLeft, interpMode)
            val ctop = NativeTransformEngine.nativeInterpolateFloat(currentFrame, kf1.frame, v1.cropTop, kf2.frame, v2.cropTop, interpMode)
            val cright = NativeTransformEngine.nativeInterpolateFloat(currentFrame, kf1.frame, v1.cropRight, kf2.frame, v2.cropRight, interpMode)
            val cbottom = NativeTransformEngine.nativeInterpolateFloat(currentFrame, kf1.frame, v1.cropBottom, kf2.frame, v2.cropBottom, interpMode)
            return CropParams(cleft, cright, ctop, cbottom, v1.cropFeather) as T
        } else if (v1 is PerspectiveParams && v2 is PerspectiveParams) {
            // Perspective params can just be step for now if too complex, but let's interpolate x/y
            return v1 as T
        }
        return v1
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun interpolateValue(v1: T, v2: T, t: Float): T {
        if (v1 is Float && v2 is Float) {
            return (v1 + (v2 - v1) * t) as T
        } else if (v1 is PairFloat && v2 is PairFloat) {
            return PairFloat(v1.first + (v2.first - v1.first) * t, v1.second + (v2.second - v1.second) * t) as T
        } else if (v1 is CropParams && v2 is CropParams) {
            return CropParams(
                v1.cropLeft + (v2.cropLeft - v1.cropLeft) * t,
                v1.cropRight + (v2.cropRight - v1.cropRight) * t,
                v1.cropTop + (v2.cropTop - v1.cropTop) * t,
                v1.cropBottom + (v2.cropBottom - v1.cropBottom) * t,
                v1.cropFeather
            ) as T
        }
        return v1
    }
}
