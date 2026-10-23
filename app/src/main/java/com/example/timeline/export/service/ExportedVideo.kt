package com.example.timeline.export.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.timeline.export.model.mp4ExportDisplayName
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
        outputDirectoryUri: String? = null,
        displayNameBase: String? = null,
        isCancelled: () -> Boolean = { false },
        onCopyProgress: (copiedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ExportedVideo {
        require(stagingFile.isFile && stagingFile.length() > 0L) {
            "The export backend did not produce a non-empty video file."
        }
        checkCancelled(isCancelled)
        val displayName = mp4ExportDisplayName(displayNameBase ?: stagingFile.nameWithoutExtension)
        val customTreeUri = outputDirectoryUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (customTreeUri != null) {
            return publishToDocumentTree(
                context = context,
                stagingFile = stagingFile,
                treeUri = customTreeUri,
                displayName = displayName,
                isCancelled = isCancelled,
                onCopyProgress = onCopyProgress
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val moviesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, Environment.DIRECTORY_MOVIES)
            val exportDirectory = File(moviesDirectory, "CinemaCutPro")
            if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                error("Android could not create the app's Movies/CinemaCutPro folder.")
            }
            val destinationFile = uniqueDestinationFile(exportDirectory, displayName)
            var published = false
            try {
                copyStagingFile(
                    stagingFile = stagingFile,
                    destinationFile = destinationFile,
                    isCancelled = isCancelled,
                    onCopyProgress = onCopyProgress
                )
                check(destinationFile.isFile && destinationFile.length() == stagingFile.length()) {
                    "The completed MP4 could not be verified in app storage."
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + PROVIDER_SUFFIX,
                    destinationFile
                )
                published = true
                stagingFile.delete()
                return ExportedVideo(
                    uri = uri,
                    displayName = destinationFile.name,
                    savedLocation = "App storage"
                )
            } finally {
                if (!published) destinationFile.delete()
            }
        }

        val resolver = context.contentResolver
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

    private fun publishToDocumentTree(
        context: Context,
        stagingFile: File,
        treeUri: Uri,
        displayName: String,
        isCancelled: () -> Boolean,
        onCopyProgress: (copiedBytes: Long, totalBytes: Long) -> Unit
    ): ExportedVideo {
        require(DocumentsContract.isTreeUri(treeUri)) {
            "The selected export location is not a writable Android folder. Choose a folder again."
        }
        val resolver = context.contentResolver
        val destination = DocumentsContract.createDocument(
            resolver,
            treeUri,
            ExportedVideo.MIME_TYPE,
            displayName
        ) ?: error("Android could not create the MP4 in the selected folder. Choose a different writable folder.")

        var published = false
        try {
            checkCancelled(isCancelled)
            val totalBytes = stagingFile.length()
            var copiedBytes = 0L
            val output = resolver.openOutputStream(destination, "w")
                ?: error("Android could not open the selected export folder for writing. Choose a different folder.")
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
            published = true
            stagingFile.delete()
            return ExportedVideo(
                uri = destination,
                displayName = displayName,
                savedLocation = "Selected folder"
            )
        } finally {
            if (!published) runCatching { DocumentsContract.deleteDocument(resolver, destination) }
        }
    }

    private fun uniqueDestinationFile(directory: File, displayName: String): File {
        val baseName = displayName.removeSuffix(".mp4")
        var candidate = File(directory, displayName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$suffix.mp4")
            suffix++
        }
        return candidate
    }

    private fun copyStagingFile(
        stagingFile: File,
        destinationFile: File,
        isCancelled: () -> Boolean,
        onCopyProgress: (copiedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val totalBytes = stagingFile.length()
        var copiedBytes = 0L
        FileInputStream(stagingFile).use { sourceStream ->
            java.io.FileOutputStream(destinationFile, false).use { destinationStream ->
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
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Export cancelled while saving the completed video.")
    }

    private const val COPY_BUFFER_BYTES = 128 * 1024
}
