package com.example.timeline.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutosaveManager(
    private val scope: CoroutineScope,
    private val versionManager: ProjectVersionManager,
    private val storage: ProjectStorage,
    private val getMetadata: (TimelineProject) -> ProjectMetadata,
    private val debounceMillis: Long = 2000L
) {
    private var saveJob: Job? = null

    fun markDirty(project: TimelineProject) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(debounceMillis)
            performAutosave(project)
        }
    }

    fun forceAutosave(project: TimelineProject) {
        saveJob?.cancel()
        performAutosave(project)
    }

    private fun performAutosave(project: TimelineProject) {
        val meta = getMetadata(project)
        storage.saveProject(project, meta)
        versionManager.createAutosaveVersion(project)
    }
}
