package com.example.model.colorgrade

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ColorGradeTarget {
    CLIP, TRACK, ADJUSTMENT_LAYER, GLOBAL
}

@Serializable
data class ColorGradeStack(
    val stackId: String = UUID.randomUUID().toString(),
    val targetType: ColorGradeTarget,
    val targetId: String,
    val enabled: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: String = "NORMAL",
    val inputTransform: LogTransformParams = LogTransformParams(),
    val primaryCorrections: ColorGradeParams = ColorGradeParams(),
    val curves: CurveParams = CurveParams(),
    val hslAdjustments: HslQualifierParams = HslQualifierParams(),
    val selectiveColor: SelectiveColorParams = SelectiveColorParams(),
    val lutStack: LutGradeParams = LutGradeParams(),
    val colorMatch: ColorMatchParams = ColorMatchParams(),
    val skinToneProtection: SkinToneProtectionParams = SkinToneProtectionParams(),
    val hdrToneMapping: HdrToneMappingParams = HdrToneMappingParams(),
    val keyframes: List<ColorGradeKeyframe> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    fun copyGrade(): ColorGradeStack = this.copy(stackId = UUID.randomUUID().toString(), keyframes = this.keyframes.toList())
    
    fun resetPrimary(): ColorGradeStack = this.copy(primaryCorrections = ColorGradeParams(), modifiedAt = System.currentTimeMillis())
    
    fun resetCurves(): ColorGradeStack = this.copy(curves = CurveParams(), modifiedAt = System.currentTimeMillis())
    
    fun resetLutStack(): ColorGradeStack = this.copy(lutStack = LutGradeParams(), modifiedAt = System.currentTimeMillis())

    fun resetAllGrade(): ColorGradeStack = ColorGradeStack(
        stackId = this.stackId,
        targetType = this.targetType,
        targetId = this.targetId
    )
    
    fun evaluateGradeAtFrame(frame: Long): ColorGradeStack {
        if (keyframes.isEmpty()) return this
        
        // This is a naive translation that overrides the static parameters if keyframes exist
        // Usually, the app would read all possible values, but we can do it defensively for core properties.
        val newSat = GradeKeyframeManager.evaluateParameter(this, "primaryCorrections.saturation", frame, primaryCorrections.saturation)
        val newContrast = GradeKeyframeManager.evaluateParameter(this, "primaryCorrections.contrast", frame, primaryCorrections.contrast)
        val newExposure = GradeKeyframeManager.evaluateParameter(this, "primaryCorrections.exposure", frame, primaryCorrections.exposure)
        
        return this.copy(
            primaryCorrections = this.primaryCorrections.copy(
                saturation = newSat,
                contrast = newContrast,
                exposure = newExposure
            )
        )
    }
}

@Serializable
data class ColorGradeParams(
    // Lift, Gamma, Gain, Offset
    val lift: ColorWheelParams = ColorWheelParams(),
    val gamma: ColorWheelParams = ColorWheelParams(),
    val gain: ColorWheelParams = ColorWheelParams(),
    val offset: ColorWheelParams = ColorWheelParams(),
    
    // Ranges
    val shadows: ColorRangeParams = ColorRangeParams(),
    val midtones: ColorRangeParams = ColorRangeParams(),
    val highlights: ColorRangeParams = ColorRangeParams(),
    
    // Basic Correction
    val temperature: Float = 0f, // -1.0 to +1.0
    val tint: Float = 0f, // -1.0 to +1.0
    val contrast: Float = 0f, // -1.0 to +1.0
    val pivot: Float = 0.5f, // 0.0 to 1.0
    val saturation: Float = 1f, // 0.0 to 2.0
    val vibrance: Float = 0f, // -1.0 to +1.0
    val exposure: Float = 0f
)

@Serializable
data class ColorWheelParams(
    val r: Float = 0f,
    val g: Float = 0f,
    val b: Float = 0f,
    val luma: Float = 0f
)

@Serializable
data class ColorRangeParams(
    val r: Float = 0f,
    val g: Float = 0f,
    val b: Float = 0f,
    val luma: Float = 0f,
    val lumaMin: Float = 0f,
    val lumaMax: Float = 1f
)

@Serializable
data class CurvePoint(val x: Float, val y: Float)

@Serializable
data class CurveParams(
    val masterPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val redPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val greenPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val bluePoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val lumaPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val hueVsHuePoints: List<CurvePoint> = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f)),
    val hueVsSatPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f)),
    val hueVsLumaPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f)),
    val satVsSatPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f)),
    val lumaVsSatPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f))
)

@Serializable
data class HslQualifierParams(
    val enabled: Boolean = false,
    val hueCenter: Float = 0.5f,
    val hueWidth: Float = 0.1f,
    val hueFeather: Float = 0.05f,
    val saturationMin: Float = 0f,
    val saturationMax: Float = 1f,
    val saturationFeather: Float = 0.1f,
    val luminanceMin: Float = 0f,
    val luminanceMax: Float = 1f,
    val luminanceFeather: Float = 0.1f,
    val invertMask: Boolean = false,
    val showMatte: Boolean = false,
    val selectedRangeColor: String? = null,
    
    // Adjustments
    val hueShift: Float = 0f,
    val saturation: Float = 1f,
    val luminance: Float = 0f,
    val contrast: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f
)

@Serializable
data class SelectiveColorRange(
    val hueShift: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
    val softness: Float = 0.1f
)

@Serializable
data class SelectiveColorParams(
    val red: SelectiveColorRange = SelectiveColorRange(),
    val orange: SelectiveColorRange = SelectiveColorRange(),
    val yellow: SelectiveColorRange = SelectiveColorRange(),
    val green: SelectiveColorRange = SelectiveColorRange(),
    val cyan: SelectiveColorRange = SelectiveColorRange(),
    val blue: SelectiveColorRange = SelectiveColorRange(),
    val purple: SelectiveColorRange = SelectiveColorRange(),
    val magenta: SelectiveColorRange = SelectiveColorRange()
)

@Serializable
data class LutGradeParams(
    val lutId: String? = null,
    val name: String = "",
    val fileName: String = "",
    val lutType: String = "CUBE_3D", // CUBE_1D or CUBE_3D
    val size: Int = 33,
    val intensity: Float = 1f,
    val interpolation: String = "TRILINEAR", // TRILINEAR, TETRAHEDRAL
    val enabled: Boolean = true,
    val orderIndex: Int = 0
)

@Serializable
data class LogTransformParams(
    val inputColorSpace: String = "Rec.709",
    val inputGamma: String = "Gamma 2.4",
    val logProfile: String = "None",
    val outputColorSpace: String = "Rec.709",
    val toneMappingMode: String = "None",
    val preserveHighlights: Boolean = false,
    val highlightRolloff: Float = 0.5f,
    val gamutMappingMode: String = "None"
)

@Serializable
data class HdrToneMappingParams(
    val enabled: Boolean = false,
    val inputNits: Float = 1000f,
    val targetNits: Float = 100f,
    val mappingAlgorithm: String = "Reinhard",
    val knee: Float = 0.5f
)

@Serializable
data class ColorMatchParams(
    val enabled: Boolean = false,
    val sourceImageId: String? = null,
    val targetMatchAmount: Float = 100f
)

@Serializable
data class SkinToneProtectionParams(
    val enabled: Boolean = false,
    val strength: Float = 0f,
    val hueRange: Float = 0.1f,
    val saturationRange: Float = 0.2f,
    val luminanceRange: Float = 0.5f,
    val skinToneLineLock: Boolean = true,
    val preserveLuminance: Boolean = true,
    val preserveHue: Boolean = false,
    val preserveSaturation: Boolean = false
)

@Serializable
data class ScopeSettings(
    val waveformEnabled: Boolean = true,
    val vectorscopeEnabled: Boolean = true,
    val histogramEnabled: Boolean = true,
    val rgbParadeEnabled: Boolean = false
)

@Serializable
data class ColorGradeKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val parameterId: String,
    val frame: Long,
    val value: Float, // Simplified string representation or float
    val interpolation: String = "LINEAR"
)

@Serializable
data class ColorGradePreset(
    val presetId: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val stack: ColorGradeStack
)

object ColorGradeHelper {
    fun defaultColorGrade(targetId: String, targetType: ColorGradeTarget = ColorGradeTarget.CLIP): ColorGradeStack {
        return ColorGradeStack(targetType = targetType, targetId = targetId)
    }

    fun pasteGrade(target: ColorGradeStack, source: ColorGradeStack): ColorGradeStack {
        return source.copy(
            stackId = target.stackId,
            targetType = target.targetType,
            targetId = target.targetId,
            modifiedAt = System.currentTimeMillis()
        ).let { ColorGradeValidator.clampStack(it) }
    }

    fun applyPreset(target: ColorGradeStack, preset: ColorGradePreset): ColorGradeStack {
        return pasteGrade(target, preset.stack)
    }
}

object ColorGradeValidator {
    
    fun clampStack(stack: ColorGradeStack): ColorGradeStack {
        return stack.copy(
            opacity = stack.opacity.coerceIn(0f, 1f),
            primaryCorrections = clampPrimary(stack.primaryCorrections),
            curves = clampCurves(stack.curves),
            hslAdjustments = clampHsl(stack.hslAdjustments),
            selectiveColor = clampSelectiveColor(stack.selectiveColor),
            lutStack = stack.lutStack.copy(intensity = stack.lutStack.intensity.coerceIn(0f, 1f)),
            skinToneProtection = clampSkinTone(stack.skinToneProtection)
        )
    }

    private fun clampPrimary(params: ColorGradeParams): ColorGradeParams {
        return params.copy(
            temperature = params.temperature.coerceIn(-1f, 1f),
            tint = params.tint.coerceIn(-1f, 1f),
            contrast = params.contrast.coerceIn(-1f, 1f),
            pivot = params.pivot.coerceIn(0f, 1f),
            saturation = params.saturation.coerceIn(0f, 2f),
            vibrance = params.vibrance.coerceIn(-1f, 1f)
        )
    }

    private fun clampCurves(params: CurveParams): CurveParams {
        return params.copy(
            masterPoints = clampCurvePoints(params.masterPoints),
            redPoints = clampCurvePoints(params.redPoints),
            greenPoints = clampCurvePoints(params.greenPoints),
            bluePoints = clampCurvePoints(params.bluePoints),
            lumaPoints = clampCurvePoints(params.lumaPoints),
            hueVsHuePoints = clampCurvePoints(params.hueVsHuePoints),
            hueVsSatPoints = clampCurvePoints(params.hueVsSatPoints),
            hueVsLumaPoints = clampCurvePoints(params.hueVsLumaPoints),
            satVsSatPoints = clampCurvePoints(params.satVsSatPoints),
            lumaVsSatPoints = clampCurvePoints(params.lumaVsSatPoints)
        )
    }

    private fun clampCurvePoints(points: List<CurvePoint>): List<CurvePoint> {
        return points.map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
    }

    private fun clampHsl(params: HslQualifierParams): HslQualifierParams {
        return params.copy(
            hueCenter = params.hueCenter.coerceIn(0f, 1f),
            hueWidth = params.hueWidth.coerceIn(0f, 1f),
            hueFeather = params.hueFeather.coerceIn(0f, 1f),
            saturationMin = params.saturationMin.coerceIn(0f, 1f),
            saturationMax = params.saturationMax.coerceIn(0f, 1f),
            saturationFeather = params.saturationFeather.coerceIn(0f, 1f),
            luminanceMin = params.luminanceMin.coerceIn(0f, 1f),
            luminanceMax = params.luminanceMax.coerceIn(0f, 1f),
            luminanceFeather = params.luminanceFeather.coerceIn(0f, 1f)
        )
    }

    private fun clampSelectiveColor(params: SelectiveColorParams): SelectiveColorParams {
        return params.copy(
            red = clampSelectiveRange(params.red),
            orange = clampSelectiveRange(params.orange),
            yellow = clampSelectiveRange(params.yellow),
            green = clampSelectiveRange(params.green),
            cyan = clampSelectiveRange(params.cyan),
            blue = clampSelectiveRange(params.blue),
            purple = clampSelectiveRange(params.purple),
            magenta = clampSelectiveRange(params.magenta)
        )
    }

    private fun clampSelectiveRange(range: SelectiveColorRange): SelectiveColorRange {
        return range.copy(
            hueShift = range.hueShift.coerceIn(-1f, 1f),
            saturation = range.saturation.coerceIn(-1f, 1f),
            luminance = range.luminance.coerceIn(-1f, 1f),
            softness = range.softness.coerceIn(0f, 1f)
        )
    }

    private fun clampSkinTone(params: SkinToneProtectionParams): SkinToneProtectionParams {
        return params.copy(
            strength = params.strength.coerceIn(0f, 1f),
            hueRange = params.hueRange.coerceIn(0f, 1f),
            saturationRange = params.saturationRange.coerceIn(0f, 1f),
            luminanceRange = params.luminanceRange.coerceIn(0f, 1f)
        )
    }
}
