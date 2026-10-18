package com.example.timeline.export.backend

import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.ExportBackendType
import com.example.timeline.export.model.ExportSettings

/** Never selects a backend unless the backend both exists and validates the exact edit. */
class ExportBackendSelector(private val factory: ExportBackendFactory) {

    fun selectOptimalBackend(settings: ExportSettings, project: TimelineProject): ExportBackend? {
        return factory.createAllBackends()
            .firstOrNull { it.backendType == ExportBackendType.MEDIA3_TRANSFORMER }
            ?.takeIf { it.getCapabilities().backendAvailable && it.validate(settings, project).isValid }
    }
}
