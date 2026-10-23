package com.example.timeline.media

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProxySourceUriResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun usesReadableLocalCopy() {
        val localFile = File(temporaryFolder.root, "cached source.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val localUri = localFile.toURI().toString()
        val originalUri = "content://media/original"

        val resolved = ProxySourceUriResolver.resolve(localUri, originalUri) { path ->
            File(path).let { it.isFile && it.length() > 0L }
        }

        assertEquals(localUri, resolved)
    }

    @Test
    fun fallsBackToOriginalWhenLocalCopyIsMissingOrEmpty() {
        val missingFile = File(temporaryFolder.root, "missing.mp4")
        val originalUri = "content://media/original"

        val resolved = ProxySourceUriResolver.resolve(missingFile.toURI().toString(), originalUri) { path ->
            File(path).let { it.isFile && it.length() > 0L }
        }

        assertEquals(originalUri, resolved)
    }

    @Test
    fun keepsNonFileLocalUrisAsThePreferredSource() {
        val localUri = "content://provider/local-copy"
        val originalUri = "content://provider/original"

        val resolved = ProxySourceUriResolver.resolve(localUri, originalUri) { false }

        assertEquals(localUri, resolved)
    }
}
