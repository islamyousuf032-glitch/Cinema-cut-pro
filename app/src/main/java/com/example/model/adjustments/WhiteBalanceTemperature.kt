package com.example.model.adjustments

/**
 * Converts an absolute white-balance Kelvin value into the relative temperature control used by
 * the preview shaders. This is an intentionally lightweight approximation; export paths that do
 * not implement absolute Kelvin balance must reject it rather than silently dropping it.
 */
object WhiteBalanceTemperature {
    const val NEUTRAL_KELVIN = 6_500f
    private const val MIN_KELVIN = 2_000f
    private const val MAX_KELVIN = 50_000f

    fun resolve(params: VideoAdjustmentParams): Float =
        resolve(params.temperature, params.temperatureKelvin)

    fun resolve(relativeTemperature: Float, temperatureKelvin: Float?): Float {
        val relative = if (relativeTemperature.isFinite()) relativeTemperature.coerceIn(-1f, 1f) else 0f
        val kelvin = temperatureKelvin?.takeIf { it.isFinite() } ?: return relative
        val boundedKelvin = kelvin.coerceIn(MIN_KELVIN, MAX_KELVIN)
        val neutralMired = 1f / NEUTRAL_KELVIN
        val kelvinMired = 1f / boundedKelvin
        val kelvinOffset = if (boundedKelvin <= NEUTRAL_KELVIN) {
            (kelvinMired - neutralMired) / (1f / MIN_KELVIN - neutralMired)
        } else {
            -(neutralMired - kelvinMired) / (neutralMired - 1f / MAX_KELVIN)
        }
        return (relative + kelvinOffset).coerceIn(-1f, 1f)
    }
}
