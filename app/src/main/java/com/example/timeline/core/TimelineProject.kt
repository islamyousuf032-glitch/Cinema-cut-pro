package com.example.timeline.core

import com.example.timeline.core.*
import com.example.timeline.media.MediaAsset
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TimelineProject(
    val id: String = UUID.randomUUID().toString(),
    val settings: ProjectSettings,
    val tracks: List<TimelineTrack> = emptyList(),
    val markers: List<TimelineMarker> = emptyList(),
    val sequences: List<TimelineSequence> = emptyList(),
    val mediaAssets: List<MediaAsset> = emptyList()
) {
    val name: String get() = settings.projectName
}
