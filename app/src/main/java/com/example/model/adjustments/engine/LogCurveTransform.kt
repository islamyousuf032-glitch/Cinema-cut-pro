package com.example.model.adjustments.engine

import com.example.model.adjustments.LogProfile

interface LogCurveTransform {
    val sourceProfile: LogProfile
    
    /**
     * Converts a non-linear LOG signal into linear light.
     * Evaluates the formula for the specific camera log curve.
     */
    fun linearize(signal: Float): Float
    
    /**
     * Optional for reverting.
     */
    fun delinearize(linear: Float): Float
}

class SLog3Transform : LogCurveTransform {
    override val sourceProfile = LogProfile.SLOG3

    override fun linearize(signal: Float): Float {
        // S-Log3 to Linear formula
        // S-Log3 curve:
        // if t >= 0.011 : linear = (10 ^ ((t - 0.420) / 0.2615)) - 0.01
        // else          : linear = (t - 0.0929) / 5.05
        return if (signal >= 0.011f) {
            (Math.pow(10.0, ((signal - 0.420f) / 0.2615f).toDouble()) - 0.01f).toFloat()
        } else {
            (signal - 0.0929f) / 5.05f
        }
    }

    override fun delinearize(linear: Float): Float {
        return if (linear >= 0.011f) {
            (0.420f + 0.2615f * Math.log10((linear + 0.01f).toDouble())).toFloat()
        } else {
            5.05f * linear + 0.0929f
        }
    }
}

class GenericLogTransform(override val sourceProfile: LogProfile) : LogCurveTransform {
    override fun linearize(signal: Float): Float = signal // Identity placeholder
    override fun delinearize(linear: Float): Float = linear
}

object LogCurveFactory {
    fun create(profile: LogProfile): LogCurveTransform {
        return when (profile) {
            LogProfile.SLOG3 -> SLog3Transform()
            // Expand with C-Log, V-Log, etc as replaceable modules
            else -> GenericLogTransform(profile)
        }
    }
}
