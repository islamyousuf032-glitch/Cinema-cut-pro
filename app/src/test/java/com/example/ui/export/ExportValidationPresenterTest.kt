package com.example.ui.export

import com.example.timeline.export.model.ExportValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportValidationPresenterTest {
    @Test
    fun doesNotRepeatFirstBlockingErrorAndSeparatesWarnings() {
        val details = ExportValidationResult(
            isValid = false,
            unsupportedFeatures = listOf("Clip must start at frame zero", "Unsupported effect"),
            warnings = listOf("Estimated source frame rate"),
            errorMessage = "Clip must start at frame zero"
        ).formatDetails()

        assertEquals(1, "Clip must start at frame zero".toRegex().findAll(details).count())
        assertTrue(details.indexOf("Blocking errors") < details.indexOf("Warnings"))
        assertTrue(details.contains("Unsupported effect"))
        assertTrue(details.contains("Estimated source frame rate"))
    }

    @Test
    fun onlyTimelineStartGapAndOverlapErrorsExposeTheTimelineAutoFix() {
        assertTrue(
            ExportValidationResult(
                isValid = false,
                unsupportedFeatures = listOf("Video gaps are not supported by Media3; use Fix automatically to close the gap."),
                warnings = emptyList(),
                errorMessage = null
            ).hasAutoFixableTimelineLayoutError()
        )
        assertFalse(
            ExportValidationResult(
                isValid = false,
                unsupportedFeatures = listOf("Variable-frame-rate sources must be conformed before export."),
                warnings = emptyList(),
                errorMessage = null
            ).hasAutoFixableTimelineLayoutError()
        )
        assertFalse(
            ExportValidationResult(
                isValid = true,
                unsupportedFeatures = listOf("Video gaps are not supported by Media3; use Fix automatically to close the gap."),
                warnings = emptyList(),
                errorMessage = null
            ).hasAutoFixableTimelineLayoutError()
        )
    }

    @Test
    fun fallsBackToErrorMessageWhenNoBlockingListIsProvided() {
        val details = ExportValidationResult(
            isValid = false,
            errorMessage = "Configuration failed"
        ).formatDetails()

        assertTrue(details.contains("Blocking errors"))
        assertTrue(details.contains("Configuration failed"))
        assertFalse(details.contains("Warnings"))
    }
}
