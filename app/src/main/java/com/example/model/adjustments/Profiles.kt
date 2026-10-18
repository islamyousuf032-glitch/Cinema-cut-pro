package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
enum class LogProfile(val displayName: String) {
    NONE("None"),
    REC_709("Rec.709"),
    REC_2020("Rec.2020"),
    HLG("HLG"),
    PQ("PQ"),
    SLOG2("S-Log2"),
    SLOG3("S-Log3"),
    CLOG("C-Log"),
    CLOG2("C-Log2"),
    CLOG3("C-Log3"),
    VLOG("V-Log"),
    LOGC("LogC"),
    NLOG("N-Log"),
    DLOG("D-Log"),
    DLOG_M("DJI D-Log M"),
    BMD_FILM("BMD Film")
}

@Serializable
enum class ColorSpaceProfile {
    REC_709,
    REC_2020,
    REC_2020_LINEAR,
    DCI_P3,
    ACES_AP0,
    ACES_AP1,
    SRGB,
    HLG,
    PQ
}
