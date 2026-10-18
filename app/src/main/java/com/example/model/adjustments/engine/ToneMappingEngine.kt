package com.example.model.adjustments.engine

enum class ToneMappingMode {
    NONE,
    SIMPLE,
    FILMIC,
    HIGHLIGHT_ROLLOFF,
    PRESERVE_SATURATION
}

interface ToneMappingEngine {
    val mode: ToneMappingMode
    
    /**
     * Maps HDR/Linear values back to 0-1 SDR range.
     */
    fun toneMap(linearR: Float, linearG: Float, linearB: Float): FloatArray
}

class FilmicToneMapping : ToneMappingEngine {
    override val mode = ToneMappingMode.FILMIC

    override fun toneMap(linearR: Float, linearG: Float, linearB: Float): FloatArray {
        // Example approximating ACES filmic curve
        val a = 2.51f
        val b = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f

        val rT = (linearR * (a * linearR + b)) / (linearR * (c * linearR + d) + e)
        val gT = (linearG * (a * linearG + b)) / (linearG * (c * linearG + d) + e)
        val bT = (linearB * (a * linearB + b)) / (linearB * (c * linearB + d) + e)

        return floatArrayOf(
            clamp(rT),
            clamp(gT),
            clamp(bT)
        )
    }

    private fun clamp(v: Float): Float = Math.max(0f, Math.min(1f, v))
}

class SimpleToneMapping : ToneMappingEngine {
    override val mode = ToneMappingMode.SIMPLE

    override fun toneMap(linearR: Float, linearG: Float, linearB: Float): FloatArray {
        // Simple Reinhard: x / (1 + x)
        return floatArrayOf(
            linearR / (1.0f + linearR),
            linearG / (1.0f + linearG),
            linearB / (1.0f + linearB)
        )
    }
}
