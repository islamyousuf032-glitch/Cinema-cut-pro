package com.example.timeline.export.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3TransformerBackendValidationRulesTest {
    @Test
    fun resolutionLimitPreservesPortraitAndLandscapeOrientations() {
        assertTrue(isWithinMedia3ResolutionLimit(1920, 1080, 3840, 2160))
        assertTrue(isWithinMedia3ResolutionLimit(1080, 1920, 3840, 2160))
        assertTrue(isWithinMedia3ResolutionLimit(3840, 2160, 3840, 2160))
        assertTrue(isWithinMedia3ResolutionLimit(2160, 3840, 3840, 2160))
    }

    @Test
    fun resolutionLimitRejectsOversizedOrInvalidDimensions() {
        assertFalse(isWithinMedia3ResolutionLimit(4096, 2160, 3840, 2160))
        assertFalse(isWithinMedia3ResolutionLimit(2160, 4096, 3840, 2160))
        assertFalse(isWithinMedia3ResolutionLimit(3000, 3000, 3840, 2160))
        assertFalse(isWithinMedia3ResolutionLimit(0, 1080, 3840, 2160))
    }
}
