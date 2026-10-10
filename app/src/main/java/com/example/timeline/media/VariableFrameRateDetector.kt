package com.example.timeline.media

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Detects timestamp cadence changes from demuxed presentation timestamps without decoding frames.
 * It intentionally ignores microsecond/time-base rounding and isolated discontinuities.
 */
internal object VariableFrameRateDetector {
    private const val MIN_INTERVAL_COUNT = 12
    private const val MIN_OUTLIERS = 2
    private const val OUTLIER_RATIO = 0.05
    private const val MIN_TOLERANCE_US = 2_000.0
    private const val RELATIVE_TOLERANCE = 0.03

    fun isVariableFrameRateSegments(timestampSegmentsUs: List<List<Long>>): Boolean =
        timestampSegmentsUs.any(::isVariableFrameRate)

    fun isVariableFrameRate(presentationTimesUs: List<Long>): Boolean {
        // MediaExtractor exposes samples in decode order for some containers; cadence is based on
        // presentation timestamps, so compare adjacent timestamps after ordering them by PTS.
        val intervals = presentationTimesUs
            .sorted()
            .zipWithNext { previous, next -> next - previous }
            .filter { it > 0L }
        if (intervals.size < MIN_INTERVAL_COUNT) return false

        val sorted = intervals.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1].toDouble() + sorted[sorted.size / 2].toDouble()) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
        val toleranceUs = maxOf(MIN_TOLERANCE_US, median * RELATIVE_TOLERANCE)
        val outlierCount = intervals.count { abs(it.toDouble() - median) > toleranceUs }
        val requiredOutliers = maxOf(MIN_OUTLIERS, ceil(intervals.size * OUTLIER_RATIO).toInt())
        return outlierCount >= requiredOutliers
    }
}
