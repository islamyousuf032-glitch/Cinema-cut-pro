package com.example.ui.export

import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.BatchExportManager
import com.example.timeline.export.service.ExportState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun createAppScopedExportFile(context: android.content.Context, extension: String): File {
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(context.filesDir, Environment.DIRECTORY_MOVIES)
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Unable to create the app's Movies export directory.")
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    val suffix = UUID.randomUUID().toString().take(8)
    return File(directory, "ChromaPro_Export_${timestamp}_${suffix}.$extension")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    project: TimelineProject,
    onDismiss: () -> Unit,
    onExportLut: (Int) -> Unit = {},
    viewModel: ExportSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val exportState by BatchExportManager.exportState.collectAsState()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LaunchedEffect(project.id) {
                viewModel.initProject(project)
            }

            if (exportState is ExportState.Exporting || exportState is ExportState.Completed || exportState is ExportState.Error) {
                ExportProgressScreen(
                    exportState = exportState,
                    onDismiss = onDismiss,
                    onRetry = { BatchExportManager.setIdle() }
                )
            } else {
                val settings = uiState.currentSettings
                if (settings != null) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Export Settings") },
                                navigationIcon = {
                                    IconButton(onClick = onDismiss) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            Surface(
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Validation Details
                                    if (!uiState.validationResult.isValid) {
                                        val errMsg = uiState.validationResult.errorMessage ?: "Configuration error"
                                        Text(
                                            "Cannot Export: $errMsg",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    } else if (uiState.validationResult.warnings.isNotEmpty()) {
                                        Text(
                                            "Warnings:\n" + uiState.validationResult.warnings.joinToString("\n") { "• $it" },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Est. Size: ~${uiState.estimatedSizeMb.toInt()} MB", style = MaterialTheme.typography.bodySmall)
                                        Text("Est. Time: ~${uiState.estimatedRenderTimeSec}s", style = MaterialTheme.typography.bodySmall)
                                    }

                                    val queueState by BatchExportManager.queueState.collectAsState()

                                    if (queueState.isNotEmpty()) {
                                        Text("In Queue: ${queueState.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = {
                                                val outFile = createAppScopedExportFile(context, settings.container.name.lowercase())
                                                BatchExportManager.enqueueExport(project, settings, outFile)
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            enabled = uiState.validationResult.isValid
                                        ) {
                                            Text("Queue")
                                        }

                                        Button(
                                            onClick = {
                                                if (queueState.isNotEmpty()) {
                                                    BatchExportManager.startQueuedExports(context)
                                                } else {
                                                    val outFile = createAppScopedExportFile(context, settings.container.name.lowercase())
                                                    BatchExportManager.startExport(context, project, settings, outFile)
                                                }
                                            },
                                            modifier = Modifier.weight(2f).height(56.dp),
                                            enabled = queueState.isNotEmpty() || uiState.validationResult.isValid
                                        ) {
                                            Text(if (queueState.isEmpty()) "Start Export" else "Start Queue", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                                        }
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            item {
                                ExportPresetSelector(
                                    selectedPreset = uiState.selectedPreset,
                                    onPresetSelected = { viewModel.selectPreset(it) }
                                )
                            }
                            item { HorizontalDivider() }
                            item {
                                ExportQualityPanel(
                                    settings = settings,
                                    onSettingsChanged = { viewModel.updateSettings { _ -> it } }
                                )
                            }
                            item { HorizontalDivider() }
                            item {
                                ExportCodecPanel(
                                    settings = settings,
                                    onSettingsChanged = { viewModel.updateSettings { _ -> it } }
                                )
                            }
                            item { HorizontalDivider() }
                            item {
                                ExportAudioPanel(
                                    settings = settings,
                                    onSettingsChanged = { viewModel.updateSettings { _ -> it } }
                                )
                            }
                            item { HorizontalDivider() }
                            item {
                                ExportColorPanel(
                                    settings = settings,
                                    onSettingsChanged = { viewModel.updateSettings { _ -> it } }
                                )
                            }
                            item { HorizontalDivider() }
                            item {
                                ExportAdvancedPanel(
                                    settings = settings,
                                    onSettingsChanged = { viewModel.updateSettings { _ -> it } },
                                    onExportLut = { onExportLut(33) } // default sizing
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
