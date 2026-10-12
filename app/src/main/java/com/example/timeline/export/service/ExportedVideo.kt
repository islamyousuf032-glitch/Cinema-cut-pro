package com.example.timeline.export.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream

/** A completed export that can be opened or shared by Android and other apps. */
data class ExportedVideo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String = "video/mp4",
    val savedLocation: String
) {
    companion object {
        const val MIME_TYPE = "video/mp4"
    }
}

/** Publishes the backend's temporary file into the user's Movies collection. */
object ExportedVideoPublisher {
    private const val RELATIVE_DIRECTORY = "Movies/CinemaCutPro"
    private const val PROVIDER_SUFFIX = ".provider"

    /**
     * Media3 Transformer writes to a filesystem path, so render to app-scoped staging first and
     * then copy the finished MP4 into MediaStore. The pending row is invisible to other apps until
     * the copy succeeds; cancellation or I/O errors remove the incomplete row.
     */
    fun publish(
        context: Context,
        stagingFile: File,
        isCancelled: () -> Boolean = { false },
        onCopyProgress: (copiedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ExportedVideo {
        require(stagingFile.isFile && stagingFile.length() > 0L) {
            "The export backend did not produce a non-empty video file."
        }
        checkCancelled(isCancelled)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + PROVIDER_SUFFIX,
                stagingFile
            )
            return ExportedVideo(
                uri = uri,
                displayName = stagingFile.name,
                savedLocation = "App storage"
            )
        }

        val resolver = context.contentResolver
        val displayName = stagingFile.name
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, ExportedVideo.MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIRECTORY)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Android could not create an export in Movies/CinemaCutPro.")

        var published = false
        try {
            val totalBytes = stagingFile.length()
            var copiedBytes = 0L
            val output = resolver.openOutputStream(destination, "w")
                ?: error("Android could not open the Movies export for writing.")
            output.use { destinationStream ->
                FileInputStream(stagingFile).use { sourceStream ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        checkCancelled(isCancelled)
                        val bytesRead = sourceStream.read(buffer)
                        if (bytesRead < 0) break
                        destinationStream.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead
                        onCopyProgress(copiedBytes, totalBytes)
                    }
                    destinationStream.flush()
                }
            }
            checkCancelled(isCancelled)

            val readyValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            check(resolver.update(destination, readyValues, null, null) > 0) {
                "Android could not publish the completed video to Movies/CinemaCutPro."
            }
            published = true
            stagingFile.delete()
            return ExportedVideo(
                uri = destination,
                displayName = displayName,
                savedLocation = RELATIVE_DIRECTORY
            )
        } finally {
            if (!published) runCatching { resolver.delete(destination, null, null) }
        }
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Export cancelled while saving to Movies.")
    }

    private const val COPY_BUFFER_BYTES = 128 * 1024
}
