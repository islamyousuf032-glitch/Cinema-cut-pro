package com.example.model.adjustments.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.colorgrade.CurveParams
import java.nio.ByteBuffer
import kotlin.math.pow

class AdjustmentShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val provider: () -> VideoAdjustmentParams
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var outputSize: Size = Size(0, 0)
    private var curveTextureId = 0
    private var uploadedCurves: CurveParams? = null
    
    init {
        try {
            glProgram = GlProgram(AdjustmentShaderSource.VERTEX_SHADER, AdjustmentShaderSource.FRAGMENT_SHADER)
        } catch (e: Exception) {
            throw RuntimeException("Failed to compile shader", e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        outputSize = Size(inputWidth, inputHeight)
        return outputSize
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val glProgram = this.glProgram ?: throw VideoFrameProcessingException("Shader not initialized")
        
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("texSampler", inputTexId, 0)
            
            
            val params = provider()
            val gradeEnabled = params.hasActivePreviewEffect()
            glProgram.setIntUniform("uGradeEnabled", if (gradeEnabled) 1 else 0)

            if (gradeEnabled) {
                AdjustmentUniformBinder.bindUniforms(glProgram, params, presentationTimeUs)
                glProgram.setFloatUniform("uSharpness", params.sharpness)
                glProgram.setFloatUniform("uResolutionX", outputSize.width.coerceAtLeast(1).toFloat())
                glProgram.setFloatUniform("uResolutionY", outputSize.height.coerceAtLeast(1).toFloat())

                val curveMask = VideoCurveTextureData.enabledMask(params.curves)
                glProgram.setIntUniform("uRgbCurvesEnabled", if ((curveMask and VideoCurveTextureData.RGB_CURVES) != 0) 1 else 0)
                glProgram.setIntUniform("uLumaCurveEnabled", if ((curveMask and VideoCurveTextureData.LUMA_CURVE) != 0) 1 else 0)
                glProgram.setIntUniform("uHueCurvesEnabled", if ((curveMask and VideoCurveTextureData.HUE_CURVES) != 0) 1 else 0)
                glProgram.setIntUniform("uSaturationCurvesEnabled", if ((curveMask and VideoCurveTextureData.SATURATION_CURVES) != 0) 1 else 0)

                val lutData = params.lutId?.let { com.example.model.colorgrade.lut.LutRepository.getCachedData(it) }
                val lutTexId = LutTextureManager.bindLutTexture(lutData)
                if (lutTexId != -1 && lutData != null) {
                    glProgram.setIntUniform("uLutEnabled", 1)
                    glProgram.setFloatUniform("uLutIntensity", params.lutIntensity)
                    glProgram.setFloatUniform("uLutSize", lutData.size.toFloat())
                    glProgram.setSamplerTexIdUniform("uLutTexture", lutTexId, 1)
                } else {
                    glProgram.setIntUniform("uLutEnabled", 0)
                    glProgram.setSamplerTexIdUniform("uLutTexture", inputTexId, 1)
                }

                // LUT upload may leave a different texture bound on the current GL unit; bind the
                // curve texture afterward so unit 2 remains correct when both effects are active.
                if (curveMask != 0) {
                    val curveTexture = ensureCurveTexture(params.curves)
                    glProgram.setSamplerTexIdUniform("uCurveTexture", curveTexture, 2)
                }
            }

            glProgram.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            glProgram.setBufferAttribute("aTexCoords", GlUtil.getTextureCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)

            glProgram.bindAttributesAndUniforms()
            
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Error adjusting video frame", e)
        }
    }
    
    private fun ensureCurveTexture(curves: CurveParams): Int {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        if (curveTextureId == 0) {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            curveTextureId = textureIds[0]
            check(curveTextureId != 0) { "Could not allocate the color-curve lookup texture" }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curveTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curveTextureId)
        if (uploadedCurves != curves) {
            val bytes = VideoCurveTextureData.createRgbaLut(curves)
            val pixels = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                position(0)
            }
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                VideoCurveTextureData.WIDTH,
                VideoCurveTextureData.HEIGHT,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixels
            )
            uploadedCurves = curves
        }
        GlUtil.checkGlError()
        return curveTextureId
    }

    override fun release() {
        super.release()
        try {
            if (curveTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(curveTextureId), 0)
                curveTextureId = 0
                uploadedCurves = null
            }
            glProgram?.delete()
        } catch (e: Exception) {
            // Ignored
        }
    }
}
