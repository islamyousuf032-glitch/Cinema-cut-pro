package com.example.timeline.export.backend

import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlRect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ByteBufferGlEffect
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.export.native.NativeExportCore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/** Creates a Media3 video effect that applies supported clip corrections in the native C++ engine. */
@OptIn(UnstableApi::class)
internal fun createMedia3ClipAdjustmentEffect(params: VideoAdjustmentParams): Effect =
    ByteBufferGlEffect(NativeClipAdjustmentProcessor(params))

@OptIn(UnstableApi::class)
private class NativeClipAdjustmentProcessor(
    params: VideoAdjustmentParams
) : ByteBufferGlEffect.Processor<ByteBuffer> {
    private val nativeParams = Media3ClipAdjustmentSupport.nativeParameters(params)
    private val reusableBuffers = ConcurrentLinkedQueue<ByteBuffer>()
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0
    @Volatile private var frameCapacity = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        require(inputWidth > 0 && inputHeight > 0) { "Color adjustment received an invalid frame size." }
        frameWidth = inputWidth
        frameHeight = inputHeight
        frameCapacity = Math.multiplyExact(Math.multiplyExact(inputWidth, inputHeight), BYTES_PER_PIXEL)
        return Size(inputWidth, inputHeight)
    }

    override fun getScaledRegion(presentationTimeUs: Long): GlRect {
        check(frameWidth > 0 && frameHeight > 0) { "Color adjustment must be configured before processing frames." }
        return GlRect(frameWidth, frameHeight)
    }

    override fun processImage(
        image: ByteBufferGlEffect.Image,
        presentationTimeUs: Long
    ): ListenableFuture<ByteBuffer> {
        check(image.width == frameWidth && image.height == frameHeight) {
            "Media3 changed frame size without reconfiguring the color-adjustment effect."
        }
        check(image.pixelBuffer.isDirect) { "Media3 color-adjustment input must be a direct buffer." }
        check(image.pixelBuffer.capacity() >= frameCapacity) { "Media3 returned an incomplete video frame." }

        val output = acquireBuffer(frameCapacity)
        var returned = false
        try {
            val source = image.pixelBuffer.duplicate().apply {
                clear()
                limit(frameCapacity)
            }
            output.put(source)
            output.flip()
            NativeExportCore.nativeApplyClipAdjustments(output, frameWidth, frameHeight, nativeParams)
            returned = true
            return Futures.immediateFuture(output)
        } finally {
            if (!returned) recycleBuffer(output)
        }
    }

    override fun finishProcessingAndBlend(
        outputFrame: GlTextureInfo,
        presentationTimeUs: Long,
        result: ByteBuffer
    ) {
        val activeTexture = IntArray(1)
        val previousTexture = IntArray(1)
        val previousUnpackAlignment = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, activeTexture, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, previousTexture, 0)
        GLES20.glGetIntegerv(GLES20.GL_UNPACK_ALIGNMENT, previousUnpackAlignment, 0)

        try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputFrame.texId)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, BYTES_PER_PIXEL)
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                frameWidth,
                frameHeight,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                result.duplicate().apply { clear() }
            )
            val error = GLES20.glGetError()
            check(error == GLES20.GL_NO_ERROR) { "OpenGL could not upload the color-adjusted frame (error $error)." }
        } finally {
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, previousUnpackAlignment[0])
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexture[0])
            GLES20.glActiveTexture(activeTexture[0])
            recycleBuffer(result)
        }
    }

    override fun release() {
        reusableBuffers.clear()
    }

    private fun acquireBuffer(capacity: Int): ByteBuffer {
        while (true) {
            val candidate = reusableBuffers.poll() ?: return ByteBuffer.allocateDirect(capacity)
            if (candidate.capacity() == capacity) return candidate.apply { clear() }
        }
    }

    private fun recycleBuffer(buffer: ByteBuffer) {
        buffer.clear()
        if (reusableBuffers.size < MAX_REUSABLE_BUFFERS) reusableBuffers.offer(buffer)
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
        const val MAX_REUSABLE_BUFFERS = 2
    }
}
