package com.example.model.adjustments.engine

/**
 * Handles HDR Electronic Optical Transfer Functions (EOTF) to decode PQ and HLG to linear light.
 */
interface HdrTransform {
    fun decodeEotf(signal: Float): Float
    fun encodeOetf(linear: Float): Float
}

class PqHdrTransform : HdrTransform {
    // SMPTE ST 2084 PQ Curve
    private val m1 = 2610.0 / 16384.0
    private val m2 = (2523.0 / 4096.0) * 128.0
    private val c1 = 3424.0 / 4096.0
    private val c2 = (2413.0 / 4096.0) * 32.0
    private val c3 = (2392.0 / 4096.0) * 32.0

    override fun decodeEotf(signal: Float): Float {
        // PQ strictly transforms 0-1 non-linear signal into 0-10000 nits linear
        val s = Math.max(0.0, signal.toDouble())
        val p = Math.pow(s, 1.0 / m2)
        val num = Math.max(p - c1, 0.0)
        val den = c2 - c3 * p
        return Math.pow(num / den, 1.0 / m1).toFloat()
    }

    override fun encodeOetf(linear: Float): Float {
        val l = Math.max(0.0, linear.toDouble())
        val p = Math.pow(l, m1)
        val num = c1 + c2 * p
        val den = 1.0 + c3 * p
        return Math.pow(num / den, m2).toFloat()
    }
}

class HlgHdrTransform : HdrTransform {
    // ARIB STD-B67 (HLG)
    private val a = 0.17883277
    private val b = 0.28466892
    private val c = 0.55991073

    override fun decodeEotf(signal: Float): Float {
        return if (signal <= 0.5f) {
            (Math.pow(signal.toDouble(), 2.0) / 3.0).toFloat()
        } else {
            (Math.exp((signal - c) / a) + b).toFloat() / 12f
        }
    }

    override fun encodeOetf(linear: Float): Float {
        // Reverse OETF (conceptually mapping linear back to HLG signal)
        return if (linear <= 1.0f / 12.0f) {
            Math.sqrt(3.0 * linear).toFloat()
        } else {
            (a * Math.log(12.0 * linear - b) + c).toFloat()
        }
    }
}
