package com.example.timeline.core

import kotlinx.serialization.Serializable

@Serializable
enum class VersionType {
    AUTOSAVE,
    MANUAL,
    NAMED
}

@Serializable
data class ProjectVersion(
    val versionId: String,
    val projectId: String,
    val type: VersionType,
    val timestamp: Long,
    val name: String? = null,
    val description: String? = null,
    val contentPath: String
)
