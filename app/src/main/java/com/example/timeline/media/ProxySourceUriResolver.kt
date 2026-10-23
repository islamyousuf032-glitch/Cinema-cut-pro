package com.example.timeline.media

import java.io.File
import java.net.URI

/** Falls back to the imported URI when a cached local source has disappeared. */
internal object ProxySourceUriResolver {
    fun resolve(
        localSource: String?,
        originalSource: String,
        isUsableLocalFile: (String) -> Boolean
    ): String {
        val local = localSource?.takeIf { it.isNotBlank() } ?: return originalSource
        val parsed = runCatching { URI(local) }.getOrNull() ?: return originalSource

        return when (parsed.scheme?.lowercase()) {
            "file" -> {
                val path = parsed.path
                if (!path.isNullOrBlank() && isUsableLocalFile(path)) local else originalSource
            }
            null -> {
                if (isUsableLocalFile(local)) File(local).toURI().toString() else originalSource
            }
            else -> local
        }
    }
}
