package com.example.model.adjustments.engine

import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint
import kotlin.math.roundToInt

/** CPU-built lookup texture shared by the Media3 preview and Transformer effects. */
internal object VideoCurveTextureData {
    const val WIDTH = 256
    const val HEIGHT = 3
    const val MAX_CONTROL_POINTS = 256

    const val RGB_CURVES = 1
    const val LUMA_CURVE = 1 shl 1
    const val HUE_CURVES = 1 shl 2
    const val SATURATION_CURVES = 1 shl 3

    private data class CurveDefinition(val name: String, val points: List<CurvePoint>)

    fun enabledMask(curves: CurveParams): Int {
        val defaults = CurveParams()
        var mask = 0
        if (curves.masterPoints != defaults.masterPoints || curves.redPoints != defaults.redPoints ||
            curves.greenPoints != defaults.greenPoints || curves.bluePoints != defaults.bluePoints
        ) mask = mask or RGB_CURVES
        if (curves.lumaPoints != defaults.lumaPoints) mask = mask or LUMA_CURVE
        if (curves.hueVsHuePoints != defaults.hueVsHuePoints ||
            curves.hueVsSatPoints != defaults.hueVsSatPoints ||
            curves.hueVsLumaPoints != defaults.hueVsLumaPoints
        ) mask = mask or HUE_CURVES
        if (curves.satVsSatPoints != defaults.satVsSatPoints ||
            curves.lumaVsSatPoints != defaults.lumaVsSatPoints
        ) mask = mask or SATURATION_CURVES
        return mask
    }

    fun validationErrors(curves: CurveParams): List<String> = definitions(curves).flatMap { definition ->
        val points = definition.points
        buildList {
            if (points.size > MAX_CONTROL_POINTS) {
                add("${definition.name} curve has more than $MAX_CONTROL_POINTS control points.")
            }
            if (points.any { point ->
                    !point.x.isFinite() || !point.y.isFinite() ||
                        point.x !in 0f..1f || point.y !in 0f..1f
                }
            ) {
                add("${definition.name} curve points must be finite and within the 0–1 range.")
            }
            if (points.zipWithNext().any { (left, right) -> right.x <= left.x }) {
                add("${definition.name} curve control-point X positions must be strictly increasing.")
            }
        }
    }

    /**
     * Packs ten curves into a 256x3 RGBA8 texture. The first row stores the master curve and
     * master-composed RGB curves; the second stores luma and hue curves; the third stores the
     * two saturation curves. Invalid imported points are sanitized for preview safety, while
     * export validation rejects them before a render starts.
     */
    fun createRgbaLut(curves: CurveParams): ByteArray {
        val safe = definitions(curves).map { sanitize(it.points) }
        val master = safe[0]
        val red = safe[1]
        val green = safe[2]
        val blue = safe[3]
        val luma = safe[4]
        val hueVsHue = safe[5]
        val hueVsSat = safe[6]
        val hueVsLuma = safe[7]
        val satVsSat = safe[8]
        val lumaVsSat = safe[9]
        val bytes = ByteArray(WIDTH * HEIGHT * 4)

        for (index in 0 until WIDTH) {
            val x = index.toFloat() / (WIDTH - 1).toFloat()
            val masterValue = evaluate(master, x)
            val pixel = index * 4
            bytes[pixel] = toByte(masterValue)
            bytes[pixel + 1] = toByte(evaluate(red, masterValue))
            bytes[pixel + 2] = toByte(evaluate(green, masterValue))
            bytes[pixel + 3] = toByte(evaluate(blue, masterValue))

            val middleRow = (WIDTH + index) * 4
            bytes[middleRow] = toByte(evaluate(luma, x))
            bytes[middleRow + 1] = toByte(evaluate(hueVsHue, x))
            bytes[middleRow + 2] = toByte(evaluate(hueVsSat, x))
            bytes[middleRow + 3] = toByte(evaluate(hueVsLuma, x))

            val bottomRow = (WIDTH * 2 + index) * 4
            bytes[bottomRow] = toByte(evaluate(satVsSat, x))
            bytes[bottomRow + 1] = toByte(evaluate(lumaVsSat, x))
            bytes[bottomRow + 2] = toByte(0.5f)
            bytes[bottomRow + 3] = toByte(0.5f)
        }
        return bytes
    }

    private fun definitions(curves: CurveParams) = listOf(
        CurveDefinition("Master", curves.masterPoints),
        CurveDefinition("Red", curves.redPoints),
        CurveDefinition("Green", curves.greenPoints),
        CurveDefinition("Blue", curves.bluePoints),
        CurveDefinition("Luma", curves.lumaPoints),
        CurveDefinition("Hue vs Hue", curves.hueVsHuePoints),
        CurveDefinition("Hue vs Sat", curves.hueVsSatPoints),
        CurveDefinition("Hue vs Luma", curves.hueVsLumaPoints),
        CurveDefinition("Sat vs Sat", curves.satVsSatPoints),
        CurveDefinition("Luma vs Sat", curves.lumaVsSatPoints)
    )

    private fun sanitize(points: List<CurvePoint>): List<CurvePoint> {
        val sanitized = points.asSequence()
            .filter { it.x.isFinite() && it.y.isFinite() }
            .map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
            .sortedBy { it.x }
            .take(MAX_CONTROL_POINTS)
            .toList()
        if (sanitized.size < 2) return sanitized

        val unique = ArrayList<CurvePoint>(sanitized.size)
        for (point in sanitized) {
            if (unique.lastOrNull()?.x == point.x) unique[unique.lastIndex] = point
            else unique += point
        }
        return unique
    }

    private fun evaluate(points: List<CurvePoint>, x: Float): Float {
        if (points.isEmpty()) return x
        if (points.size == 1) return points[0].y

        var left = points.first()
        if (x <= left.x) return left.y
        for (index in 1 until points.size) {
            val right = points[index]
            if (x <= right.x) {
                val amount = (x - left.x) / (right.x - left.x).coerceAtLeast(0.0001f)
                return left.y + (right.y - left.y) * amount
            }
            left = right
        }
        return points.last().y
    }

    private fun toByte(value: Float): Byte =
        (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
}
