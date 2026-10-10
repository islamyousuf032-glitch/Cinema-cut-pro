package com.example.timeline.export.backend

import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.ExportBackendType
import com.example.timeline.export.model.ExportJob
import com.example.timeline.export.model.ExportProgress
import com.example.timeline.export.model.ExportSettings
import com.example.timeline.export.model.ExportValidationResult

interface ExportBackend {
    val backendType: ExportBackendType
    
    fun getCapabilities(): ExportBackendCapability
    
    fun validate(settings: ExportSettings, project: TimelineProject): ExportValidationResult
    
    suspend fun prepare(job: ExportJob): Boolean
    
    suspend fun render(job: ExportJob, onProgress: (ExportProgress) -> Unit)
    
    fun cancel(jobId: String)
    
    fun release()
}
