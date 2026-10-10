package com.example.timeline.core

import kotlinx.serialization.Serializable

@Serializable
data class ProjectMetadata(
    val id: String,
    val name: String,
    val createdDate: Long,
    val modifiedDate: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val thumbnailPath: String? = null
)
