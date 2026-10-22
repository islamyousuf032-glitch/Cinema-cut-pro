package com.example.timeline.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import java.io.File
import java.io.IOException

/** Thin Kotlin/JNI boundary; frame parsing and Bayer decoding remain native C++. */
internal object NativeMcrawDecoder {
    private val libraryFailure: String? = try {
        System.loadLibrary("motioncam_preview")
        null
    } catch (error: UnsatisfiedLinkError) {
        error.message ?: "Could not load motioncam_preview"
    }

    private fun requireLibrary() {
        libraryFailure?.let { throw IOException("Native MotionCam decoder unavailable: $it") }
    }

    fun open(context: Context, uri: Uri): Long {
        requireLibrary()
        val descriptor = openDescriptor(context, uri)
        val handle = descriptor.use { nativeOpen(it.fd) }
        if (handle == 0L) throw IOException("Native MotionCam decoder returned an invalid handle")
        return handle
    }

    private fun openDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: throw IOException("File URI has no path")
            val file = File(path)
            if (!file.isFile || !file.canRead()) throw IOException("MotionCam file is not readable")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Could not open MotionCam media URI")
    }

    fun close(handle: Long) {
        if (handle == 0L || libraryFailure != null) return
        nativeClose(handle)
    }

    fun frameCount(handle: Long): Int {
        requireLibrary()
        return nativeGetFrameCount(handle)
    }

    fun frameTimestampNs(handle: Long, index: Int): Long {
        requireLibrary()
        return nativeGetFrameTimestampNs(handle, index)
    }

    fun containerMetadataJson(handle: Long): String {
        requireLibrary()
        return nativeGetContainerMetadataJson(handle)
    }

    fun frameMetadataJson(handle: Long, index: Int): String {
        requireLibrary()
        return nativeGetFrameMetadataJson(handle, index)
    }

    fun decodeFrame(
        handle: Long,
        index: Int,
        maximumEdge: Int = 960,
        gradeParams: FloatArray? = null
    ): Bitmap {
        requireLibrary()
        return nativeDecodeFrameToBitmap(handle, index, maximumEdge, gradeParams)
            ?: throw IOException(lastError(handle).ifBlank { "Could not render MotionCam RAW frame" })
    }

    fun audioSampleRateHz(handle: Long): Int {
        requireLibrary()
        return nativeGetAudioSampleRateHz(handle)
    }

    fun audioChannelCount(handle: Long): Int {
        requireLibrary()
        return nativeGetAudioChannelCount(handle)
    }

    fun hasGyroData(handle: Long): Boolean {
        requireLibrary()
        return nativeHasGyroData(handle)
    }

    fun hasAccelerometerData(handle: Long): Boolean {
        requireLibrary()
        return nativeHasAccelerometerData(handle)
    }

    fun resetAudio(handle: Long): Boolean {
        requireLibrary()
        return nativeResetAudio(handle)
    }

    /** Copies the next signed-16-bit PCM chunk into [samples] and writes its source timestamp. */
    fun readNextAudioChunk(handle: Long, samples: ShortArray, timestampNs: LongArray): Int {
        requireLibrary()
        return nativeReadNextAudioChunk(handle, samples, timestampNs)
    }

    fun lastError(handle: Long): String {
        if (handle == 0L || libraryFailure != null) return libraryFailure.orEmpty()
        return nativeGetLastError(handle)
    }

    private external fun nativeOpen(fd: Int): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeGetFrameCount(handle: Long): Int
    private external fun nativeGetFrameTimestampNs(handle: Long, index: Int): Long
    private external fun nativeGetContainerMetadataJson(handle: Long): String
    private external fun nativeGetFrameMetadataJson(handle: Long, index: Int): String
    private external fun nativeDecodeFrameToBitmap(
        handle: Long,
        index: Int,
        maximumEdge: Int,
        gradeParams: FloatArray?
    ): Bitmap?
    private external fun nativeGetAudioSampleRateHz(handle: Long): Int
    private external fun nativeGetAudioChannelCount(handle: Long): Int
    private external fun nativeHasGyroData(handle: Long): Boolean
    private external fun nativeHasAccelerometerData(handle: Long): Boolean
    private external fun nativeResetAudio(handle: Long): Boolean
    private external fun nativeReadNextAudioChunk(handle: Long, samples: ShortArray, timestampNs: LongArray): Int
    private external fun nativeGetLastError(handle: Long): String
}
