package com.example.timeline.core

import java.util.UUID

class ProjectVersionManager(private val storage: ProjectStorage) {

    fun createManualVersion(project: TimelineProject, name: String, description: String = ""): ProjectVersion {
        val version = ProjectVersion(
            versionId = UUID.randomUUID().toString(),
            projectId = project.id,
            type = VersionType.MANUAL,
            timestamp = System.currentTimeMillis(),
            name = name,
            description = description,
            contentPath = ""
        )
        storage.saveVersion(project, version)
        return version
    }

    fun createAutosaveVersion(project: TimelineProject): ProjectVersion {
        val version = ProjectVersion(
            versionId = "autosave", // Stable ID so autosaves overwrite each other to avoid unlimited growth
            projectId = project.id,
            type = VersionType.AUTOSAVE,
            timestamp = System.currentTimeMillis(),
            name = "Autosave",
            description = "Automatic backup",
            contentPath = ""
        )
        storage.saveVersion(project, version)
        return version
    }

    fun listVersions(projectId: String): List<ProjectVersion> {
        return storage.listVersions(projectId)
    }

    fun restoreVersion(projectId: String, versionId: String): TimelineProject? {
        return storage.loadVersionContent(projectId, versionId)
    }

    fun duplicateVersion(projectId: String, versionId: String, newProjectName: String): TimelineProject? {
        val versionProject = storage.loadVersionContent(projectId, versionId) ?: return null
        return versionProject.copy(
            id = UUID.randomUUID().toString(),
            settings = versionProject.settings.copy(projectName = newProjectName)
        )
    }
}
