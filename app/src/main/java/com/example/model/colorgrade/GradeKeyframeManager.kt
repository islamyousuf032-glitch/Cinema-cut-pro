package com.example.model.colorgrade

import java.util.UUID

object GradeKeyframeManager {

    enum class InterpolationType(val id: String) {
        HOLD("HOLD"),
        LINEAR("LINEAR"),
        EASE("EASE")
    }

    fun addOrUpdateKeyframe(
        stack: ColorGradeStack,
        parameterId: String,
        frame: Long,
        value: Float,
        interpolation: InterpolationType = InterpolationType.LINEAR
    ): ColorGradeStack {
        val existingKeyframes = stack.keyframes.toMutableList()
        val index = existingKeyframes.indexOfFirst { it.parameterId == parameterId && it.frame == frame }
        
        val newKeyframe = ColorGradeKeyframe(
            id = if (index != -1) existingKeyframes[index].id else UUID.randomUUID().toString(),
            parameterId = parameterId,
            frame = frame,
            value = value,
            interpolation = interpolation.id
        )

        if (index != -1) {
            existingKeyframes[index] = newKeyframe
        } else {
            existingKeyframes.add(newKeyframe)
            existingKeyframes.sortBy { it.frame }
        }

        return stack.copy(keyframes = existingKeyframes, modifiedAt = System.currentTimeMillis())
    }

    fun removeKeyframe(stack: ColorGradeStack, parameterId: String, frame: Long): ColorGradeStack {
        val filtered = stack.keyframes.filterNot { it.parameterId == parameterId && it.frame == frame }
        return stack.copy(keyframes = filtered, modifiedAt = System.currentTimeMillis())
    }

    fun evaluateParameter(stack: ColorGradeStack, parameterId: String, frame: Long, defaultValue: Float): Float {
        val parameterKeyframes = stack.keyframes
            .filter { it.parameterId == parameterId }
            .sortedBy { it.frame }

        if (parameterKeyframes.isEmpty()) return defaultValue
        if (parameterKeyframes.size == 1) return parameterKeyframes.first().value

        val firstK = parameterKeyframes.first()
        val lastK = parameterKeyframes.last()

        if (frame <= firstK.frame) return firstK.value
        if (frame >= lastK.frame) return lastK.value

        for (i in 0 until parameterKeyframes.size - 1) {
            val k1 = parameterKeyframes[i]
            val k2 = parameterKeyframes[i + 1]

            if (frame in k1.frame..k2.frame) {
                if (k1.frame == k2.frame) return k1.value
                val fraction = (frame - k1.frame).toFloat() / (k2.frame - k1.frame).toFloat()
                
                return when (k1.interpolation) {
                    InterpolationType.HOLD.id -> k1.value
                    InterpolationType.EASE.id -> {
                        // Smoothstep ease
                        val easeFraction = fraction * fraction * (3f - 2f * fraction)
                        k1.value + (k2.value - k1.value) * easeFraction
                    }
                    else -> k1.value + (k2.value - k1.value) * fraction // LINEAR
                }
            }
        }

        return defaultValue
    }
}
