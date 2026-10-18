package com.example.timeline.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TimelineSequence(
    val id: String = UUID.randomUUID().toString(),
    val settings: ProjectSettings,
    val tracks: List<TimelineTrack> = emptyList(),
    val markers: List<TimelineMarker> = emptyList()
) {
    val duration: Long
        get() = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEnd } ?: 0L
    val name: String get() = settings.projectName
}

fun TimelineSequence.toProject(sequences: List<TimelineSequence> = emptyList()): TimelineProject = TimelineProject(
    id = id,
    settings = settings,
    tracks = tracks,
    markers = markers,
    sequences = sequences
)

fun TimelineProject.toSequence(): TimelineSequence = TimelineSequence(
    id = id,
    settings = settings,
    tracks = tracks,
    markers = markers
)
