package com.example.timeline.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Surface
import java.io.File
import java.io.IOException

/** JNI surface for FFmpeg demuxing and native MediaCodec/software video decoding. */
internal object NativeFfmpegDecoder {
    private val libraryFailure: String? = try {
        System.loadLibrary("ffmpeg_decoder")
        null
    } catch (error: UnsatisfiedLinkError) {
        error.message ?: "Could not load ffmpeg_decoder"
    }

    private fun requireLibrary() {
        libraryFailure?.let { throw IOException("FFmpeg playback is unavailable: $it") }
    }

    fun open(context: Context, uri: Uri, surface: Surface): Long {
        requireLibrary()
        val handle = openDescriptor(context, uri).use { descriptor ->
            nativeOpen(descriptor.fd, surface)
        }
        if (handle == 0L) throw IOException("FFmpeg did not create a decoder handle")
        return handle
    }

    private fun openDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
        if (uri.scheme.isNullOrBlank() || uri.scheme.equals("file", ignoreCase = true)) {
            val path = if (uri.scheme.isNullOrBlank()) {
                uri.toString()
            } else {
                uri.path ?: throw IOException("Video file URI has no path")
            }
            val file = File(path)
            if (!file.isFile || !file.canRead()) throw IOException("Video file is not readable")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Could not open the selected video source")
    }

    fun close(handle: Long) {
        if (handle != 0L && libraryFailure == null) nativeClose(handle)
    }

    fun play(handle: Long) {
        requireLibrary()
        nativePlay(handle)
    }

    fun pause(handle: Long) {
        if (handle != 0L && libraryFailure == null) nativePause(handle)
    }

    fun seek(handle: Long, positionUs: Long) {
        requireLibrary()
        nativeSeek(handle, positionUs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(handle: Long, speed: Float) {
        requireLibrary()
        nativeSetPlaybackSpeed(handle, speed)
    }

    fun positionUs(handle: Long): Long = if (handle == 0L) 0L else nativeGetPositionUs(handle)
    fun durationUs(handle: Long): Long = if (handle == 0L) 0L else nativeGetDurationUs(handle)
    fun isPlaying(handle: Long): Boolean = handle != 0L && nativeIsPlaying(handle)
    fun hasFirstFrame(handle: Long): Boolean = handle != 0L && nativeHasFirstFrame(handle)

    fun lastError(handle: Long): String = when {
        handle != 0L && libraryFailure == null -> nativeGetLastError(handle)
        else -> libraryFailure.orEmpty()
    }

    private external fun nativeOpen(descriptor: Int, surface: Surface): Long
    private external fun nativeClose(handle: Long)
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeSeek(handle: Long, positionUs: Long)
    private external fun nativeSetPlaybackSpeed(handle: Long, speed: Float)
    private external fun nativeGetPositionUs(handle: Long): Long
    private external fun nativeGetDurationUs(handle: Long): Long
    private external fun nativeIsPlaying(handle: Long): Boolean
    private external fun nativeHasFirstFrame(handle: Long): Boolean
    private external fun nativeGetLastError(handle: Long): String
}
