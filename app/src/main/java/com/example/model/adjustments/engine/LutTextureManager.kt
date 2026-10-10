package com.example.model.adjustments.engine

import android.opengl.GLES30
import androidx.media3.common.util.GlUtil
import com.example.model.colorgrade.lut.LutData
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LutTextureManager {
    private var currentLutId: String? = null
    private var lutTexId: Int = -1
    private var lutSize: Int = 0

    fun bindLutTexture(lutData: LutData?): Int {
        if (lutData == null) {
            deleteTexture()
            return -1
        }
        
        val id = lutData.title + "_" + lutData.size
        if (currentLutId == id && lutTexId != -1) {
            return lutTexId
        }

        deleteTexture()
        currentLutId = id
        lutSize = lutData.size
        
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        lutTexId = textures[0]

        val expectedSize = lutSize * lutSize * lutSize
        val pixels = ByteBuffer.allocateDirect(expectedSize * 4) // RGBA 8-bit
        pixels.order(ByteOrder.nativeOrder())
        
        for (i in 0 until expectedSize) {
            val r = (lutData.cubeData[i * 3] * 255f).toInt().coerceIn(0, 255)
            val g = (lutData.cubeData[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
            val b = (lutData.cubeData[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
            pixels.put(r.toByte())
            pixels.put(g.toByte())
            pixels.put(b.toByte())
            pixels.put(255.toByte())
        }
        pixels.position(0)
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTexId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 
            lutSize, lutSize * lutSize, 0, 
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels
        )
        
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        return lutTexId
    }

    private fun deleteTexture() {
        if (lutTexId != -1) {
            val textures = intArrayOf(lutTexId)
            GLES30.glDeleteTextures(1, textures, 0)
            lutTexId = -1
        }
        currentLutId = null
    }

    fun release() {
        deleteTexture()
    }
}
