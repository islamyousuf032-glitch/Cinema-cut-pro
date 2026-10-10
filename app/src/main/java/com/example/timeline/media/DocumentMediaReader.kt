package com.example.timeline.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import java.util.UUID

object DocumentMediaReader {
    fun createAnalyzingAsset(context: Context, uri: Uri, projectId: String): MediaAsset? {
        var name = "Unknown Media"
        var sizeBytes = 0L
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        var lastModified = System.currentTimeMillis()

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                    
                    val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (flagsIndex != -1) {
                         val modified = cursor.getLong(flagsIndex)
                         if (modified > 0) lastModified = modified
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val assetType = when {
            mimeType.startsWith("video/") -> MediaAssetType.VIDEO
            mimeType.startsWith("audio/") -> MediaAssetType.AUDIO
            mimeType.startsWith("image/") -> MediaAssetType.IMAGE
            else -> MediaAssetType.UNKNOWN
        }

        return MediaAsset(
            assetId = UUID.randomUUID().toString(),
            projectId = projectId,
            displayName = name,
            originalUriString = uri.toString(),
            persistedUriPermission = false,
            mimeType = mimeType,
            fileSizeBytes = sizeBytes,
            importedAt = System.currentTimeMillis(),
            modifiedAt = lastModified,
            assetType = assetType,
            previewStatus = PreviewStatus.NOT_TESTED,
            proxyStatus = ProxyStatus.NONE,
            mediaErrorStatus = MediaErrorStatus.NONE,
            mediaAvailabilityStatus = MediaAvailabilityStatus.AVAILABLE_ORIGINAL_URI,
            metadata = MediaMetadata(
                durationUs = 0L,
                durationFramesInSourceRate = 0L,
                containerFormat = "",
                bitrate = 0L,
                width = 0,
                height = 0,
                rotationDegrees = 0,
                pixelAspectRatio = 1.0f,
                videoStreams = emptyList(),
                audioStreams = emptyList(),
                hasVideo = assetType == MediaAssetType.VIDEO,
                hasAudio = assetType == MediaAssetType.AUDIO,
                isVariableFrameRate = false,
                estimatedFrameRate = null,
                exactFrameRate = null,
                timecodeStart = null,
                creationTime = lastModified
            )
        )
    }
}
