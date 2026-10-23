package com.example.timeline.media

import java.util.UUID

/** Helpers for reconciling the project snapshot with WorkManager after process recreation. */
internal object ProxyGenerationRecovery {
    const val INTERRUPTED_MESSAGE =
        "Proxy generation was interrupted before its work request could be saved. Retry proxy generation."
    const val MISSING_WORK_MESSAGE =
        "The proxy work request is no longer available. Retry proxy generation."

    fun workIdFor(status: ProxyStatus, rawWorkId: String?): UUID? {
        if (status != ProxyStatus.GENERATING || rawWorkId.isNullOrBlank()) return null
        return runCatching { UUID.fromString(rawWorkId) }
            .getOrNull()
            ?.takeIf { it.toString() == rawWorkId }
    }

    fun wasInterrupted(status: ProxyStatus, rawWorkId: String?): Boolean =
        status == ProxyStatus.GENERATING && workIdFor(status, rawWorkId) == null
}
