package com.example.timeline.ui

/** Non-viewport editor panels need a live frame, but do not need 30 UI-state writes per second. */
internal fun shouldPublishPlaybackPlayhead(
    lastPublishedAtMs: Long,
    nowMs: Long,
    minimumIntervalMs: Long = 100L
): Boolean = lastPublishedAtMs < 0L || nowMs - lastPublishedAtMs >= minimumIntervalMs
