package com.example.timeline.media

import android.system.Os
import java.io.File
import java.util.UUID

/** Publishes completed proxy files without exposing an in-progress/partial MP4 as READY. */
internal object ProxyOutputFiles {
    fun stagingFileFor(outputFile: File, token: String = UUID.randomUUID().toString()): File {
        val parent = requireNotNull(outputFile.parentFile) { "Proxy output must have a parent directory." }
        val extension = outputFile.extension.ifBlank { "mp4" }
        val baseName = outputFile.nameWithoutExtension.ifBlank { "proxy" }
        return File(parent, "$baseName.pending-$token.$extension")
    }

    fun publish(stagingFile: File, outputFile: File) {
        val stagingParent = requireNotNull(stagingFile.parentFile).canonicalFile
        val outputParent = requireNotNull(outputFile.parentFile).canonicalFile
        require(stagingParent == outputParent) { "Proxy staging and destination files must share a directory." }
        // POSIX rename replaces the prior file atomically when both files are on the same volume.
        Os.rename(stagingFile.absolutePath, outputFile.absolutePath)
    }
}
