package com.example.timeline.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableFrameRateDetectorTest {
    @Test
    fun constantFrameRateTimestampRoundingIsNotMarkedVariable() {
        val timestampsUs = (0L..120L).map { frame -> frame * 1_000_000L * 1001L / 24_000L }

        assertFalse(VariableFrameRateDetector.isVariableFrameRate(timestampsUs))
    }

    @Test
    fun repeatedCadenceChangesAreMarkedVariable() {
        val timestampsUs = mutableListOf(0L)
        repeat(120) { index ->
            val intervalUs = if (index % 8 == 0) 50_000L else 33_333L
            timestampsUs += timestampsUs.last() + intervalUs
        }

        assertTrue(VariableFrameRateDetector.isVariableFrameRate(timestampsUs))
    }

    @Test
    fun presentationTimestampsAreSortedBeforeCadenceAnalysis() {
        val timestamps = (0L..80L).map { frame -> frame * 33_333L }.shuffled()
        assertFalse(VariableFrameRateDetector.isVariableFrameRate(timestamps))
    }

    @Test
    fun cadenceChangesInAnySampledSegmentAreDetected() {
        val constantSegment = (0L..80L).map { frame -> frame * 33_333L }
        val variableSegment = mutableListOf(0L)
        repeat(80) { index ->
            val intervalUs = if (index % 6 == 0) 50_000L else 33_333L
            variableSegment += variableSegment.last() + intervalUs
        }

        assertTrue(
            VariableFrameRateDetector.isVariableFrameRateSegments(
                listOf(constantSegment, variableSegment, constantSegment)
            )
        )
    }

    @Test
    fun anIsolatedTimestampGapIsTreatedAsDiscontinuityNotVfr() {
        val timestampsUs = mutableListOf(0L)
        repeat(120) { index ->
            val intervalUs = if (index == 60) 250_000L else 33_333L
            timestampsUs += timestampsUs.last() + intervalUs
        }

        assertFalse(VariableFrameRateDetector.isVariableFrameRate(timestampsUs))
    }

    @Test
    fun tooFewSamplesDoNotClaimThatCadenceIsConstantOrVariable() {
        val timestampsUs = listOf(0L, 33_333L, 66_666L, 99_999L)

        assertFalse(VariableFrameRateDetector.isVariableFrameRate(timestampsUs))
    }
}
