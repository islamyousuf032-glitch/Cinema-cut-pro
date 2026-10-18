package com.example.timeline.media

import kotlin.math.abs
import kotlin.math.ceil

internal enum class FrameRateCadence {
    UNKNOWN,
    CONSTANT,
    VARIABLE
}

/**
 * Classifies cadence using MediaExtractor presentation timestamps. Timestamp intervals within five
 * percent of their median are treated as CFR; isolated discontinuities are not sufficient evidence
 * of VFR. UNKNOWN is kept distinct from CFR when the probe has too few usable timestamps.
 */
internal object VariableFrameRateDetector {
    private const val MIN_INTERVAL_COUNT = 12
    private const val MIN_OUTLIERS = 2
    private const val OUTLIER_RATIO = 0.05
    private const val MIN_TOLERANCE_US = 500.0
    private const val RELATIVE_TOLERANCE = 0.05

    fun classifySegments(timestampSegmentsUs: List<List<Long>>): FrameRateCadence {
        val classifications = timestampSegmentsUs.map(::classify)
        return when {
            classifications.any { it == FrameRateCadence.VARIABLE } -> FrameRateCadence.VARIABLE
            classifications.any { it == FrameRateCadence.CONSTANT } -> FrameRateCadence.CONSTANT
            else -> FrameRateCadence.UNKNOWN
        }
    }

    fun classify(presentationTimesUs: List<Long>): FrameRateCadence {
        // MediaExtractor exposes samples in decode order for some containers; cadence is based on
        // presentation timestamps, so compare adjacent timestamps after ordering them by PTS.
        val intervals = presentationTimesUs
            .sorted()
            .zipWithNext { previous, next -> next - previous }
            .filter { it > 0L }
        if (intervals.size < MIN_INTERVAL_COUNT) return FrameRateCadence.UNKNOWN

        val sorted = intervals.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1].toDouble() + sorted[sorted.size / 2].toDouble()) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
        val toleranceUs = maxOf(MIN_TOLERANCE_US, median * RELATIVE_TOLERANCE)
        val outlierCount = intervals.count { abs(it.toDouble() - median) > toleranceUs }
        val requiredOutliers = maxOf(MIN_OUTLIERS, ceil(intervals.size * OUTLIER_RATIO).toInt())
        return if (outlierCount >= requiredOutliers) FrameRateCadence.VARIABLE else FrameRateCadence.CONSTANT
    }

    // Retained for existing call sites/tests while callers migrate to the tri-state result.
    fun isVariableFrameRateSegments(timestampSegmentsUs: List<List<Long>>): Boolean =
        classifySegments(timestampSegmentsUs) == FrameRateCadence.VARIABLE

    fun isVariableFrameRate(presentationTimesUs: List<Long>): Boolean =
        classify(presentationTimesUs) == FrameRateCadence.VARIABLE
}
