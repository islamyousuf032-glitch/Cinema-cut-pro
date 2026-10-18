package com.example.model.colorgrade.engine

import com.example.model.colorgrade.LogTransformParams
import com.example.model.colorgrade.HdrToneMappingParams
import kotlin.math.pow

object ColorSpaceTransformEngine {

    fun applyInputTransform(rgb: FloatArray, params: LogTransformParams) {
        if (params.inputColorSpace == "Rec.709" && params.logProfile == "None") return
        
        // This is a placeholder for real color space transformation matrices 
        // e.g., decoding S-Log3, C-Log, etc. back to linear Rec.709 or working space.
        
        if (params.logProfile.contains("Log")) {
            // simplistic log expansion
            val expand = 1.2f
            rgb[0] = rgb[0].pow(expand)
            rgb[1] = rgb[1].pow(expand)
            rgb[2] = rgb[2].pow(expand)
        }
    }
}

object LutProcessor {
    fun process(rgb: FloatArray, params: com.example.model.colorgrade.LutGradeParams) {
        val lutId = params.lutId
        if (!params.enabled || lutId == null || params.intensity == 0f) return
        
        val lutData = com.example.model.colorgrade.lut.LutRepository.getCachedData(lutId) ?: return
        
        val r = rgb[0].coerceIn(0f, 1f)
        val g = rgb[1].coerceIn(0f, 1f)
        val b = rgb[2].coerceIn(0f, 1f)

        val size = lutData.size
        
        // Trilinear interpolation
        val rPos = r * (size - 1)
        val gPos = g * (size - 1)
        val bPos = b * (size - 1)
        
        val r0 = rPos.toInt().coerceIn(0, size - 2)
        val g0 = gPos.toInt().coerceIn(0, size - 2)
        val b0 = bPos.toInt().coerceIn(0, size - 2)
        
        val r1 = r0 + 1
        val g1 = g0 + 1
        val b1 = b0 + 1
        
        val rd = rPos - r0
        val gd = gPos - g0
        val bd = bPos - b0
        
        val data = lutData.cubeData

        fun getLutVal(rx: Int, gx: Int, bx: Int, ch: Int): Float {
            val idx = ((bx * size + gx) * size + rx) * 3 + ch
            return data[idx]
        }
        
        for (i in 0..2) {
            val c000 = getLutVal(r0, g0, b0, i)
            val c100 = getLutVal(r1, g0, b0, i)
            val c010 = getLutVal(r0, g1, b0, i)
            val c110 = getLutVal(r1, g1, b0, i)
            val c001 = getLutVal(r0, g0, b1, i)
            val c101 = getLutVal(r1, g0, b1, i)
            val c011 = getLutVal(r0, g1, b1, i)
            val c111 = getLutVal(r1, g1, b1, i)
            
            val c00 = c000 * (1 - rd) + c100 * rd
            val c01 = c001 * (1 - rd) + c101 * rd
            val c10 = c010 * (1 - rd) + c110 * rd
            val c11 = c011 * (1 - rd) + c111 * rd
            
            val c0 = c00 * (1 - gd) + c10 * gd
            val c1 = c01 * (1 - gd) + c11 * gd
            
            val c = c0 * (1 - bd) + c1 * bd
            
            rgb[i] = rgb[i] * (1 - params.intensity) + c * params.intensity
        }
    }
}

object HdrToneMappingProcessor {
    fun process(rgb: FloatArray, params: HdrToneMappingParams) {
        if (!params.enabled) return
        
        // Simple Reinhard tone mapping
        if (params.mappingAlgorithm == "Reinhard") {
            rgb[0] = rgb[0] / (rgb[0] + 1f)
            rgb[1] = rgb[1] / (rgb[1] + 1f)
            rgb[2] = rgb[2] / (rgb[2] + 1f)
        }
    }
}
