package com.example.timeline.export.model

/** Returns a filesystem/provider-safe base name; the MP4 suffix is added by the publisher. */
fun normalizeExportFileBaseName(value: String): String {
    val leafName = value.trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .removeSuffixIgnoreCase(".mp4")
    val safeName = buildString(leafName.length) {
        leafName.forEach { character ->
            append(
                if (character.isLetterOrDigit() || character in " -_.()") {
                    character
                } else {
                    '_'
                }
            )
        }
    }.trim(' ', '.', '_')
        .take(MAX_BASE_NAME_LENGTH)
        .trimEnd(' ', '.', '_')
    return safeName.ifBlank { DEFAULT_BASE_NAME }
}

fun mp4ExportDisplayName(value: String): String = "${normalizeExportFileBaseName(value)}.mp4"

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

private const val MAX_BASE_NAME_LENGTH = 80
private const val DEFAULT_BASE_NAME = "CinemaCutPro_Export"
