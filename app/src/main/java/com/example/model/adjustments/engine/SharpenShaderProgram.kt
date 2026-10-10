package com.example.model.adjustments.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import com.example.model.adjustments.VideoAdjustmentParams

class SharpenShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val provider: () -> VideoAdjustmentParams
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var outputSize = Size.UNKNOWN
    
    init {
        try {
            glProgram = GlProgram(AdjustmentShaderSource.VERTEX_SHADER, AdjustmentShaderSource.SHARPEN_FRAGMENT_SHADER)
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
            
            glProgram.setFloatUniform("uSharpness", params.sharpness)
            glProgram.setFloatUniform("uResolutionX", outputSize.width.toFloat())
            glProgram.setFloatUniform("uResolutionY", outputSize.height.toFloat())
            
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
