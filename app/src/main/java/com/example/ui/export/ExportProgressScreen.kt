package com.example.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.timeline.export.BatchExportManager
import com.example.timeline.export.service.ExportState
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportProgressScreen(
    exportState: ExportState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (exportState is ExportState.Completed) "Export Complete" else "Exporting") },
                navigationIcon = {
                    if (exportState !is ExportState.Exporting) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val state = exportState) {
                is ExportState.Exporting -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { state.percent },
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(state.stage, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(state.percent * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${state.renderedFrames} / ${state.totalFrames} frames", style = MaterialTheme.typography.bodyMedium)
                        if (state.estimatedRemainingMs > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Est. Remaining: ${state.estimatedRemainingMs / 1000}s", style = MaterialTheme.typography.bodyMedium)
                        }

                        val queueState by BatchExportManager.queueState.collectAsState()
                        if (queueState.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Queue: ${queueState.size} remaining", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { BatchExportManager.cancelExport(context); onDismiss() }) {
                            Text("Cancel Export")
                        }
                    }
                }
                is ExportState.Completed -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_dialog_info),
                            contentDescription = "Success",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Export Finished!", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Saved to: ${state.outputFile.name}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = {
                                ExportShareManager.shareVideo(context, state.outputFile)
                            }) {
                                Text("Share")
                            }
                            Button(onClick = {
                                ExportShareManager.playVideo(context, state.outputFile)
                            }) {
                                Text("Play")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { BatchExportManager.setIdle(); onDismiss() }) {
                            Text("Close")
                        }
                    }
                }
                is ExportState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_dialog_alert),
                            contentDescription = "Error",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Export Failed", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = onRetry) {
                                Text("Try Again")
                            }
                            OutlinedButton(onClick = { BatchExportManager.setIdle(); onDismiss() }) {
                                Text("Close")
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
