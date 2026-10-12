package com.example.ui.export

import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.BatchExportManager
import com.example.timeline.export.validation.TimelineExportAutoFixer
import com.example.timeline.export.service.ExportState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun createStagingExportFile(context: android.content.Context, extension: String): File {
    val moviesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(context.filesDir, Environment.DIRECTORY_MOVIES)
    val stagingDirectory = File(moviesDirectory, ".cinema_cut_pro_staging")
    if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
        throw IllegalStateException("Unable to create the temporary export directory.")
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    val suffix = UUID.randomUUID().toString().take(8)
    return File(stagingDirectory, "CinemaCutPro_Export_${timestamp}_${suffix}.$extension")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    project: TimelineProject,
    onDismiss: () -> Unit,
    onExportLut: (Int) -> Unit = {},
    onFixTimeline: () -> Boolean = { false },
    viewModel: ExportSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val compactHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val uiState by viewModel.uiState.collectAsState()
    val exportState by BatchExportManager.exportState.collectAsState()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LaunchedEffect(project) {
                viewModel.syncProject(project)
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
                    var showValidationDetails by remember { mutableStateOf(false) }
                    val validationDetails = uiState.validationResult.formatDetails()
                    val autoFixAvailable = remember(project, uiState.validationResult) {
                        uiState.validationResult.hasAutoFixableTimelineLayoutError() &&
                            TimelineExportAutoFixer.compactVisibleVideoTrack(project) != null
                    }

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
                                Column(modifier = Modifier.padding(if (compactHeight) 8.dp else 16.dp)) {
                                    if (!uiState.validationResult.isValid) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Cannot Export: ${uiState.validationResult.errorMessage ?: "Configuration error"}",
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (uiState.validationResult.warnings.isNotEmpty()) {
                                                    Text(
                                                        "${uiState.validationResult.warnings.size} non-blocking warning(s)",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                            if (autoFixAvailable) {
                                                TextButton(onClick = { onFixTimeline() }) {
                                                    Text("Fix automatically")
                                                }
                                            }
                                            TextButton(onClick = { showValidationDetails = true }) {
                                                Text("Details")
                                            }
                                        }
                                    } else if (uiState.validationResult.warnings.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "${uiState.validationResult.warnings.size} export warning(s)",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { showValidationDetails = true }) {
                                                Text("Details")
                                            }
                                        }
                                    }
                                    
                                    if (uiState.validationResult.isValid) {
                                        if (compactHeight) {
                                            Text(
                                                "Est. size ~${uiState.estimatedSizeMb.toInt()} MB  •  time ~${uiState.estimatedRenderTimeSec}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Est. Size: ~${uiState.estimatedSizeMb.toInt()} MB", style = MaterialTheme.typography.bodySmall)
                                                Text("Est. Time: ~${uiState.estimatedRenderTimeSec}s", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }

                                    val queueState by BatchExportManager.queueState.collectAsState()

                                    if (queueState.isNotEmpty()) {
                                        Text("In Queue: ${queueState.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = if (compactHeight) 0.dp else 8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val outFile = createStagingExportFile(context, settings.container.name.lowercase())
                                                BatchExportManager.enqueueExport(project, settings, outFile)
                                            },
                                            modifier = Modifier.weight(1f).height(if (compactHeight) 48.dp else 56.dp),
                                            enabled = uiState.validationResult.isValid
                                        ) {
                                            Text("Queue")
                                        }

                                        Button(
                                            onClick = {
                                                if (queueState.isNotEmpty()) {
                                                    BatchExportManager.startQueuedExports(context)
                                                } else {
                                                    val outFile = createStagingExportFile(context, settings.container.name.lowercase())
                                                    BatchExportManager.startExport(context, project, settings, outFile)
                                                }
                                            },
                                            modifier = Modifier.weight(2f).height(if (compactHeight) 48.dp else 56.dp),
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
                    if (showValidationDetails) {
                        AlertDialog(
                            onDismissRequest = { showValidationDetails = false },
                            title = { Text(if (uiState.validationResult.isValid) "Export warnings" else "Export validation details") },
                            text = {
                                Text(
                                    validationDetails,
                                    modifier = Modifier
                                        .heightIn(max = 320.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showValidationDetails = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
