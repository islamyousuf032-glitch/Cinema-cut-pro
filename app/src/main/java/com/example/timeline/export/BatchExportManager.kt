package com.example.timeline.export

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.backend.ExportBackend
import com.example.timeline.export.backend.ExportBackendFactory
import com.example.timeline.export.backend.ExportBackendSelector
import com.example.timeline.export.model.ExportJob
import com.example.timeline.export.model.ExportJobStatus
import com.example.timeline.export.model.ExportSettings
import com.example.timeline.export.service.ExportCancelController
import com.example.timeline.export.service.ExportForegroundService
import com.example.timeline.export.service.ExportNotification
import com.example.timeline.export.service.ExportProgressRepository
import com.example.timeline.export.service.ExportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object BatchExportManager {
    data class QueuedExport(
        val project: TimelineProject,
        val settings: ExportSettings,
        val outputFile: File
    )

    private val exportLock = Any()
    private val exportQueue = mutableListOf<QueuedExport>()
    private var serviceJobActive = false
    @Volatile var currentExport: QueuedExport? = null
    @Volatile var currentBackend: ExportBackend? = null
    val queueState = MutableStateFlow<List<QueuedExport>>(emptyList())
    val exportState = ExportProgressRepository.exportState

    /** Adds an export without starting the foreground worker, for a staged batch queue. */
    fun enqueueExport(project: TimelineProject, settings: ExportSettings, outputFile: File) {
        synchronized(exportLock) {
            exportQueue.add(QueuedExport(project, settings, outputFile))
            queueState.value = exportQueue.toList()
        }
    }

    fun startExport(context: Context, project: TimelineProject, settings: ExportSettings, outputFile: File) {
        enqueueExport(project, settings, outputFile)
        startQueuedExports(context)
    }

    fun startQueuedExports(context: Context) {
        val shouldStartService = synchronized(exportLock) {
            if (!serviceJobActive && currentExport == null && exportQueue.isNotEmpty()) {
                serviceJobActive = true
                true
            } else {
                false
            }
        }
        if (shouldStartService) startNextInQueue(context)
    }

    private fun startNextInQueue(context: Context) {
        val promoted = synchronized(exportLock) {
            when {
                currentExport != null -> false
                exportQueue.isEmpty() -> {
                    serviceJobActive = false
                    false
                }
                else -> promoteNextQueuedExportLocked()
            }
        }
        if (!promoted) return
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = "ACTION_START_EXPORT"
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /** Promotes the next queued job for the already-running foreground service. */
    fun promoteNextQueuedExport(): Boolean = synchronized(exportLock) {
        promoteNextQueuedExportLocked()
    }

    private fun promoteNextQueuedExportLocked(): Boolean {
        if (currentExport != null || exportQueue.isEmpty()) return false
        currentExport = exportQueue.removeAt(0)
        queueState.value = exportQueue.toList()
        ExportCancelController.reset()
        ExportProgressRepository.updateState(ExportState.Exporting("Preparing", 0, 1, 0f, 0L, 0L))
        return true
    }

    /** Called only after the foreground service's worker has stopped. */
    fun onExportServiceStopped(context: Context) {
        val shouldRestart = synchronized(exportLock) {
            serviceJobActive = false
            if (currentExport == null && exportQueue.isNotEmpty()) {
                serviceJobActive = true
                true
            } else {
                false
            }
        }
        if (shouldRestart) startNextInQueue(context)
    }

    fun cancelExport(context: Context) {
        ExportCancelController.cancel()
        currentBackend?.cancel(currentExport?.settings?.exportId ?: "")
        currentBackend?.release()
        currentBackend = null

        ExportProgressRepository.updateState(ExportState.Idle)
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = "ACTION_CANCEL_EXPORT"
        }
        context.startService(intent)

        synchronized(exportLock) {
            exportQueue.clear()
            queueState.value = emptyList()
            currentExport = null
        }
    }

    fun setIdle() {
        ExportProgressRepository.updateState(ExportState.Idle)
    }

    suspend fun runExportInternal(serviceContext: Context) {
        val export = currentExport ?: return
        val project = export.project
        val settings = export.settings
        val outputFile = export.outputFile
        val jobId = settings.exportId
        val startTimeMs = System.currentTimeMillis()
        var selectedBackend: ExportBackend? = null
        var shouldAdvanceQueue = false

        try {
            val validationErrors = com.example.timeline.export.validation.ExportValidator
                .validate(serviceContext, project, settings)
            if (validationErrors.isNotEmpty()) {
                val errorMsg = "Validation failed:\n" + validationErrors.joinToString("\n") { it.message }
                ExportProgressRepository.updateState(ExportState.Error(errorMsg))
                ExportNotification.showErrorNotification(serviceContext, "Export Validation Failed")
                shouldAdvanceQueue = true
                return
            }

            val factory = ExportBackendFactory(serviceContext)
            val selector = ExportBackendSelector(factory)
            val backend = selector.selectOptimalBackend(settings, project)
                ?: throw IllegalArgumentException(
                    factory.validateWithAvailableBackend(settings, project).errorMessage
                        ?: "No compatible real export backend is available for this edit."
                )
            selectedBackend = backend
            currentBackend = backend

            val job = ExportJob(
                jobId = jobId,
                projectId = project.id,
                settings = settings,
                status = ExportJobStatus.QUEUED,
                outputUri = outputFile.absolutePath
            )

            if (!backend.prepare(job)) {
                val errorMsg = "The MP4 export backend could not prepare the output file."
                ExportProgressRepository.updateState(ExportState.Error(errorMsg))
                ExportNotification.showErrorNotification(serviceContext, errorMsg)
                outputFile.delete()
                shouldAdvanceQueue = true
                return
            }

            var lastNotificationTime = 0L
            var receivedTerminalProgress = false

            backend.render(job) { progress ->
                if (ExportCancelController.isCancelled) return@render

                val now = System.currentTimeMillis()
                val elapsedMs = now - startTimeMs
                val percentFinished = (progress.progressPercent / 100f).coerceIn(0f, 1f)
                val estimatedRemaining = if (percentFinished > 0f && percentFinished < 1f) {
                    ((elapsedMs / percentFinished) - elapsedMs).toLong().coerceAtLeast(0L)
                } else 0L

                val stageString = when (progress.status) {
                    ExportJobStatus.RENDERING_VIDEO -> "Rendering Video"
                    ExportJobStatus.RENDERING_AUDIO -> "Rendering Audio"
                    ExportJobStatus.ENCODING -> progress.currentStage.ifBlank { "Encoding" }
                    ExportJobStatus.MUXING -> "Muxing"
                    else -> progress.currentStage.ifBlank { "Processing" }
                }

                when (progress.status) {
                    ExportJobStatus.COMPLETED -> {
                        receivedTerminalProgress = true
                        shouldAdvanceQueue = true
                        ExportProgressRepository.updateState(ExportState.Completed(outputFile))
                        ExportNotification.showCompletedNotification(serviceContext, outputFile)
                    }
                    ExportJobStatus.FAILED -> {
                        receivedTerminalProgress = true
                        shouldAdvanceQueue = true
                        val errorMsg = progress.errorMessage ?: "Export failed"
                        ExportProgressRepository.updateState(ExportState.Error(errorMsg))
                        ExportNotification.showErrorNotification(serviceContext, errorMsg)
                        outputFile.delete()
                    }
                    else -> {
                        ExportProgressRepository.updateState(
                            ExportState.Exporting(
                                stage = stageString,
                                renderedFrames = progress.renderedFrames,
                                totalFrames = progress.totalFrames,
                                percent = percentFinished,
                                elapsedMs = elapsedMs,
                                estimatedRemainingMs = estimatedRemaining
                            )
                        )

                        if (now - lastNotificationTime > 500) {
                            ExportNotification.updateProgress(
                                serviceContext,
                                progress.progressPercent.toInt().coerceIn(0, 100),
                                "$stageString ${progress.progressPercent.toInt().coerceIn(0, 100)}%"
                            )
                            lastNotificationTime = now
                        }
                    }
                }
            }

            if (!receivedTerminalProgress && !ExportCancelController.isCancelled) {
                val errorMsg = "The export backend stopped without reporting success or failure."
                ExportProgressRepository.updateState(ExportState.Error(errorMsg))
                ExportNotification.showErrorNotification(serviceContext, errorMsg)
                outputFile.delete()
                shouldAdvanceQueue = true
            }
        } catch (cancelled: CancellationException) {
            outputFile.delete()
            ExportProgressRepository.updateState(ExportState.Idle)
            throw cancelled
        } catch (failure: Exception) {
            val mappedError = com.example.timeline.export.validation.ExportErrorMapper.mapExceptionToError(failure)
            val recoveryActions = com.example.timeline.export.validation.ExportErrorMapper.getRecoveryActions(mappedError)
            Log.e("BatchExportManager", "Export failed: ${mappedError.message}", mappedError.exception)

            val errorMsg = mappedError.message + if (recoveryActions.isNotEmpty()) {
                "\n\nSuggested actions:\n" + recoveryActions.joinToString("\n") { "- " + it.name.replace('_', ' ') }
            } else ""

            ExportProgressRepository.updateState(ExportState.Error(errorMsg))
            ExportNotification.showErrorNotification(serviceContext, errorMsg)
            outputFile.delete()
            shouldAdvanceQueue = true
        } finally {
            runCatching { selectedBackend?.release() }
            if (currentBackend === selectedBackend) currentBackend = null
            if (shouldAdvanceQueue) {
                synchronized(exportLock) {
                    if (currentExport?.settings?.exportId == jobId) currentExport = null
                }
            }
        }
    }
}
