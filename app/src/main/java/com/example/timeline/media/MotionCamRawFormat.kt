package com.example.timeline.media

import android.content.Context
import android.net.Uri
import java.io.IOException

object MotionCamRawFormat {
    const val MIME_TYPE = "application/x-motioncam-raw"
    private const val CONTAINER_ID = "MOTION "

    fun isMcrawAsset(asset: MediaAsset): Boolean {
        return asset.mimeType.equals(MIME_TYPE, ignoreCase = true) ||
            asset.displayName.endsWith(".mcraw", ignoreCase = true) ||
            asset.metadata.originalFilename.orEmpty().endsWith(".mcraw", ignoreCase = true) ||
            asset.originalUriString.endsWith(".mcraw", ignoreCase = true) ||
            asset.localOriginalUriString.orEmpty().endsWith(".mcraw", ignoreCase = true)
    }

    /** Checks the 8-byte MCRAW header when the document provider reports an opaque file type. */
    fun isCandidate(context: Context, asset: MediaAsset, uri: Uri): Boolean {
        if (isMcrawAsset(asset)) return true
        return isCandidate(context, asset.mimeType, uri)
    }

    fun isCandidate(context: Context, mimeType: String?, uri: Uri): Boolean {
        if (mimeType.equals(MIME_TYPE, ignoreCase = true) ||
            uri.toString().endsWith(".mcraw", ignoreCase = true)) return true
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(8)
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count <= 0) return@use false
                    offset += count
                }
                String(header, 0, 7, Charsets.US_ASCII) == CONTAINER_ID
            } ?: false
        }.getOrDefault(false)
    }

    fun openErrorMessage(error: Throwable): String {
        return when (error) {
            is IOException -> error.message ?: "Could not read this MotionCam recording."
            else -> error.message ?: "Could not analyze this MotionCam recording."
        }
    }
}
