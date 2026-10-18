package com.example.timeline.core

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectAspectRatioPreset(val displayName: String) {
    W_16_9("16:9 Widescreen"),
    W_9_16("9:16 Vertical"),
    W_1_1("1:1 Square"),
    W_4_5("4:5 Social Portrait"),
    W_5_4("5:4"),
    W_3_2("3:2"),
    W_4_3("4:3"),
    W_21_9("21:9 Ultra Wide"),
    W_2_35_1("2.35:1 Cinematic"),
    W_2_39_1("2.39:1 Cinema Scope"),
    CUSTOM("Custom")
}

@Serializable
data class Rational(val numerator: Int, val denominator: Int) {
    fun toFloat(): Float = numerator.toFloat() / denominator.toFloat()
    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()
    override fun toString(): String = if (denominator == 1) "$numerator" else "$numerator/$denominator"
}

@Serializable
enum class ProjectFrameRatePreset(val displayName: String, val fps: Rational) {
    FPS_23_976("23.976 fps", Rational(24000, 1001)),
    FPS_24("24 fps", Rational(24, 1)),
    FPS_25("25 fps", Rational(25, 1)),
    FPS_29_97("29.97 fps", Rational(30000, 1001)),
    FPS_30("30 fps", Rational(30, 1)),
    FPS_50("50 fps", Rational(50, 1)),
    FPS_59_94("59.94 fps", Rational(60000, 1001)),
    FPS_60("60 fps", Rational(60, 1)),
    FPS_120("120 fps", Rational(120, 1)),
    CUSTOM("Custom", Rational(0, 1))
}

@Serializable
enum class ProjectColorSpace(val displayName: String) {
    REC_709("Rec.709 SDR"),
    REC_2020("Rec.2020"),
    HDR_HLG("HDR HLG"),
    HDR_PQ("HDR PQ"),
    LOG_MANAGED("LOG managed workflow"),
    AUTOMATIC("Automatic")
}

@Serializable
enum class ProjectAudioSampleRate(val displayName: String, val hz: Int) {
    HZ_44100("44.1 kHz", 44100),
    HZ_48000("48 kHz", 48000),
    HZ_96000("96 kHz", 96000)
}

@Serializable
enum class ProjectAudioChannels(val displayName: String, val channels: Int) {
    MONO("Mono", 1),
    STEREO("Stereo", 2),
    SURROUND_5_1("5.1 Surround", 6)
}

@Serializable
data class ProjectSettings(
    val projectId: String,
    val projectName: String,
    val aspectRatioPreset: ProjectAspectRatioPreset = ProjectAspectRatioPreset.W_16_9,
    val customAspectRatioWidth: Int = 16,
    val customAspectRatioHeight: Int = 9,
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val frameRate: ProjectFrameRatePreset = ProjectFrameRatePreset.FPS_24,
    val customFrameRateNumerator: Int = 24,
    val customFrameRateDenominator: Int = 1,
    val colorSpace: ProjectColorSpace = ProjectColorSpace.REC_709,
    val workingColorSpace: ProjectColorSpace = ProjectColorSpace.REC_709,
    val bitDepthPreference: Int = 8,
    val audioSampleRate: ProjectAudioSampleRate = ProjectAudioSampleRate.HZ_48000,
    val audioChannels: ProjectAudioChannels = ProjectAudioChannels.STEREO,
    val canvasBackgroundColor: String = "#000000",
    val startTimecode: String = "00:00:00:00",
    val exportPreset: com.example.timeline.export.model.ExportSettings? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    val aspectRatioWidth: Int
        get() = when (aspectRatioPreset) {
            ProjectAspectRatioPreset.W_16_9 -> 16
            ProjectAspectRatioPreset.W_9_16 -> 9
            ProjectAspectRatioPreset.W_1_1 -> 1
            ProjectAspectRatioPreset.W_4_5 -> 4
            ProjectAspectRatioPreset.W_5_4 -> 5
            ProjectAspectRatioPreset.W_3_2 -> 3
            ProjectAspectRatioPreset.W_4_3 -> 4
            ProjectAspectRatioPreset.W_21_9 -> 21
            ProjectAspectRatioPreset.W_2_35_1 -> 235
            ProjectAspectRatioPreset.W_2_39_1 -> 239
            ProjectAspectRatioPreset.CUSTOM -> customAspectRatioWidth
            else -> 16
        }

    val aspectRatioHeight: Int
        get() = when (aspectRatioPreset) {
            ProjectAspectRatioPreset.W_16_9 -> 9
            ProjectAspectRatioPreset.W_9_16 -> 16
            ProjectAspectRatioPreset.W_1_1 -> 1
            ProjectAspectRatioPreset.W_4_5 -> 5
            ProjectAspectRatioPreset.W_5_4 -> 4
            ProjectAspectRatioPreset.W_3_2 -> 2
            ProjectAspectRatioPreset.W_4_3 -> 3
            ProjectAspectRatioPreset.W_21_9 -> 9
            ProjectAspectRatioPreset.W_2_35_1 -> 100
            ProjectAspectRatioPreset.W_2_39_1 -> 100
            ProjectAspectRatioPreset.CUSTOM -> customAspectRatioHeight
            else -> 9
        }

    val aspectRatioFloat: Float
        get() = resolutionWidth.toFloat() / resolutionHeight.toFloat()

    fun getFpsRational(): Rational {
        return if (frameRate == ProjectFrameRatePreset.CUSTOM) {
             Rational(customFrameRateNumerator, customFrameRateDenominator)
        } else {
             frameRate.fps
        }
    }
}

@Serializable
enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT,
    ADJUSTMENT,
    NESTED_TIMELINE
}

@Serializable
enum class ClipType {
    MEDIA,
    TEXT,
    ADJUSTMENT,
    NESTED_SEQUENCE
}
