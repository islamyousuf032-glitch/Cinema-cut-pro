package com.example.timeline.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProxyGenerationRecoveryTest {
    @Test
    fun generatingAssetWithSavedWorkIdCanBeReattachedAfterProcessRecreation() {
        val workId = UUID.randomUUID()

        assertEquals(
            workId,
            ProxyGenerationRecovery.workIdFor(ProxyStatus.GENERATING, workId.toString())
        )
        assertFalse(ProxyGenerationRecovery.wasInterrupted(ProxyStatus.GENERATING, workId.toString()))
    }

    @Test
    fun generatingAssetWithoutAValidWorkIdIsMarkedInterrupted() {
        assertNull(ProxyGenerationRecovery.workIdFor(ProxyStatus.GENERATING, null))
        assertNull(ProxyGenerationRecovery.workIdFor(ProxyStatus.GENERATING, "not-a-uuid"))
        assertTrue(ProxyGenerationRecovery.wasInterrupted(ProxyStatus.GENERATING, null))
        assertTrue(ProxyGenerationRecovery.wasInterrupted(ProxyStatus.GENERATING, "not-a-uuid"))
    }

    @Test
    fun nonGeneratingAssetsAreNotTreatedAsInterrupted() {
        assertNull(ProxyGenerationRecovery.workIdFor(ProxyStatus.READY, UUID.randomUUID().toString()))
        assertFalse(ProxyGenerationRecovery.wasInterrupted(ProxyStatus.READY, null))
    }
}
