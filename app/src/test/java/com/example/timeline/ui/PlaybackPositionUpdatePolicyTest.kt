package com.example.timeline.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionUpdatePolicyTest {
    @Test
    fun playbackPanelsAreUpdatedImmediatelyThenAtMostTenTimesPerSecond() {
        assertTrue(shouldPublishPlaybackPlayhead(lastPublishedAtMs = -1L, nowMs = 1_000L))
        assertFalse(shouldPublishPlaybackPlayhead(lastPublishedAtMs = 1_000L, nowMs = 1_099L))
        assertTrue(shouldPublishPlaybackPlayhead(lastPublishedAtMs = 1_000L, nowMs = 1_100L))
    }

    @Test
    fun customIntervalsCanBeUsedForUiSampling() {
        assertFalse(
            shouldPublishPlaybackPlayhead(
                lastPublishedAtMs = 200L,
                nowMs = 249L,
                minimumIntervalMs = 50L
            )
        )
        assertTrue(
            shouldPublishPlaybackPlayhead(
                lastPublishedAtMs = 200L,
                nowMs = 250L,
                minimumIntervalMs = 50L
            )
        )
    }
}
