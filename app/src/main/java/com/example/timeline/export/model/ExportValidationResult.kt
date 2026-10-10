package com.example.timeline.export.model

data class ExportValidationResult(
    val isValid: Boolean,
    val unsupportedFeatures: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)
