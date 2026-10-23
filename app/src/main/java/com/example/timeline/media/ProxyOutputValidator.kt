package com.example.timeline.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/** Structural postcondition for a proxy: reopenable MP4 with encoded video (and requested audio). */
internal object ProxyOutputValidator {
    fun verify(outputFile: File, expectAudio: Boolean) {
        check(outputFile.isFile && outputFile.length() > 0L) {
            "Proxy output is missing or empty."
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(outputFile.absolutePath)
            val trackFormats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val videoTrackIndices = trackFormats.indices.filter { index ->
                trackFormats[index].getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            check(videoTrackIndices.isNotEmpty()) {
                "Media3 reported proxy completion, but the MP4 has no readable video track."
            }

            val videoTrackIndex = videoTrackIndices.first()
            val videoFormat = trackFormats[videoTrackIndex]
            check(
                videoFormat.getInteger(MediaFormat.KEY_WIDTH) > 0 &&
                    videoFormat.getInteger(MediaFormat.KEY_HEIGHT) > 0
            ) {
                "The proxy video track has invalid dimensions."
            }
            check(
                !videoFormat.containsKey(MediaFormat.KEY_DURATION) ||
                    videoFormat.getLong(MediaFormat.KEY_DURATION) > 0L
            ) {
                "The proxy video track has no usable duration."
            }

            extractor.selectTrack(videoTrackIndex)
            check(hasReadableSample(extractor, videoFormat)) {
                "The proxy video track contains no readable encoded samples."
            }

            if (expectAudio) {
                val audioTrackIndex = trackFormats.indices.firstOrNull { index ->
                    trackFormats[index].getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                check(audioTrackIndex != null) {
                    "Audio was requested, but the generated proxy has no audio track."
                }
                extractor.unselectTrack(videoTrackIndex)
                val audioFormat = trackFormats[audioTrackIndex]
                extractor.selectTrack(audioTrackIndex)
                check(hasReadableSample(extractor, audioFormat)) {
                    "The proxy audio track contains no readable encoded samples."
                }
            }
        } catch (failure: Exception) {
            throw IllegalStateException(
                failure.message?.takeIf(String::isNotBlank)
                    ?: "The generated proxy could not be reopened as a playable MP4.",
                failure
            )
        } finally {
            extractor.release()
        }
    }

    private fun hasReadableSample(extractor: MediaExtractor, format: MediaFormat): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return extractor.sampleSize > 0L
        val maximumInputSize = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val bufferCapacity = maxOf(MIN_SAMPLE_BUFFER_BYTES, maximumInputSize)
            .coerceAtMost(MAX_SAMPLE_BUFFER_BYTES)
        return runCatching {
            extractor.readSampleData(ByteBuffer.allocateDirect(bufferCapacity), 0) > 0
        }.getOrDefault(false)
    }

    private const val MIN_SAMPLE_BUFFER_BYTES = 64 * 1024
    private const val MAX_SAMPLE_BUFFER_BYTES = 4 * 1024 * 1024
}
