package com.example.timeline.engine.preview

import androidx.media3.exoplayer.ExoPlayer

/**
 * Handles synchronizing ExoPlayer playback state with timeline playhead.
 */
class PreviewPlaybackSynchronizer(
    private val player: ExoPlayer,
    private val fpsNumerator: Int,
    private val fpsDenominator: Int
) {

    fun syncToTimelineFrame(projectFrame: Long, sourceFrame: Long) {
        val targetPositionMs = TimelineFrameResolver.convertFrameToMicroseconds(sourceFrame, fpsNumerator, fpsDenominator) / 1000
        
        // Only seek if the difference is significant to avoid stuttering
        if (Math.abs(player.currentPosition - targetPositionMs) > 100) {
            player.seekTo(targetPositionMs)
        }
    }

    fun sourcePositionToTimelineFrame(
        playerPosMs: Long,
        timelineStart: Long,
        sourceIn: Long
    ): Long {
        val playerUs = playerPosMs * 1000
        val fps = if (fpsDenominator > 0) fpsNumerator.toDouble() / fpsDenominator.toDouble() else 30.0
        val sourceFrame = (playerUs.toDouble() / 1_000_000.0 * fps).toLong()
        val offsetFrames = sourceFrame - sourceIn
        return timelineStart + offsetFrames
    }
}
