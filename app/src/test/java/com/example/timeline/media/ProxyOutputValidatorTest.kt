package com.example.timeline.media

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProxyOutputValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rejectsEmptyOutputBeforeMarkingProxyReady() {
        val output = File(temporaryFolder.root, "empty-proxy.mp4").apply { createNewFile() }

        val failure = runCatching {
            ProxyOutputValidator.verify(output, expectAudio = false)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("missing or empty"))
    }
}
