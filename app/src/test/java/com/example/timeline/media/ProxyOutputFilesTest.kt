package com.example.timeline.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProxyOutputFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagingOutputKeepsMp4ExtensionAndSharesDestinationDirectory() {
        val output = File(temporaryFolder.root, "proxy_asset.mp4")
        val staging = ProxyOutputFiles.stagingFileFor(output, token = "attempt-1")

        assertEquals("proxy_asset.pending-attempt-1.mp4", staging.name)
        assertEquals(output.parentFile, staging.parentFile)
        assertTrue(staging.name.endsWith(".mp4"))
    }
}
