package com.example.model.adjustments.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import com.example.model.adjustments.VideoAdjustmentParams
import kotlin.math.pow

class AdjustmentShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val provider: () -> VideoAdjustmentParams
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var outputSize: Size = Size(0, 0)
    
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
            
            AdjustmentUniformBinder.bindUniforms(glProgram, params, presentationTimeUs)
            glProgram.setFloatUniform("uSharpness", params.sharpness)
            glProgram.setFloatUniform("uResolutionX", outputSize.width.coerceAtLeast(1).toFloat())
            glProgram.setFloatUniform("uResolutionY", outputSize.height.coerceAtLeast(1).toFloat())

            val lutData = params.lutId?.let { com.example.model.colorgrade.lut.LutRepository.getCachedData(it) }
            val lutTexId = LutTextureManager.bindLutTexture(lutData)
            if (lutTexId != -1 && lutData != null) {
                glProgram.setIntUniform("uLutEnabled", 1)
                glProgram.setFloatUniform("uLutIntensity", params.lutIntensity)
                glProgram.setFloatUniform("uLutSize", lutData.size.toFloat())
                glProgram.setSamplerTexIdUniform("uLutTexture", lutTexId, 1)
            } else {
                glProgram.setIntUniform("uLutEnabled", 0)
                glProgram.setSamplerTexIdUniform("uLutTexture", inputTexId, 1) // Provide a dummy texture to avoid crash
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
    
    override fun release() {
        super.release()
        try {
            glProgram?.delete()
        } catch (e: Exception) {
            // Ignored
        }
    }
}
