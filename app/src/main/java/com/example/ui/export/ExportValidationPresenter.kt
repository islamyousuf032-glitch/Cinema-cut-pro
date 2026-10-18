package com.example.ui.export

import com.example.timeline.export.model.ExportValidationResult

/** Formats blocking validation errors and non-blocking warnings as separate sections. */
internal fun ExportValidationResult.hasAutoFixableTimelineLayoutError(): Boolean {
    if (isValid) return false
    val errors = (unsupportedFeatures + listOfNotNull(errorMessage)).distinct()
    return errors.any { error ->
        error.startsWith("The supported clip must start at timeline frame zero") ||
            error.startsWith("Video gaps are not supported by Media3") ||
            error.startsWith("Overlapping clips on the same video track")
    }
}

internal fun ExportValidationResult.formatDetails(): String {
    val blockingErrors = (
        (if (isValid) emptyList() else listOfNotNull(errorMessage?.trim())) + unsupportedFeatures.map(String::trim)
    ).filter { it.isNotEmpty() }.distinct()
    val distinctWarnings = warnings.map(String::trim).filter { it.isNotEmpty() }.distinct()

    return buildString {
        if (blockingErrors.isNotEmpty()) {
            appendLine("Blocking errors")
            blockingErrors.forEach { appendLine("• $it") }
        }
        if (distinctWarnings.isNotEmpty()) {
            if (isNotEmpty()) appendLine()
            appendLine("Warnings")
            distinctWarnings.forEach { appendLine("• $it") }
        }
    }.trim().ifEmpty { "No additional validation details." }
}
