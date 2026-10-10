package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.export.model.ExportFrameRate
import com.example.timeline.export.model.ExportQualityPreset
import com.example.timeline.export.model.ExportSettings
import java.util.Locale

@Composable
fun ExportQualityPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Video Quality", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        // Resolution
        val resOptions = listOf(Pair(1280, 720), Pair(1920, 1080), Pair(3840, 2160))
        Column {
            Text("Resolution", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                resOptions.forEach { res ->
                    val isSelected = settings.resolutionWidth == res.first && settings.resolutionHeight == res.second
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSettingsChanged(settings.copy(resolutionWidth = res.first, resolutionHeight = res.second)) },
                        label = { Text("${res.second}p") }
                    )
                }
            }
        }

        // Frame Rate
        val allFrameRates = listOf(
            ExportFrameRate.FPS_23_976, ExportFrameRate.FPS_24, ExportFrameRate.FPS_25,
            ExportFrameRate.FPS_29_97, ExportFrameRate.FPS_30, ExportFrameRate.FPS_50,
            ExportFrameRate.FPS_59_94, ExportFrameRate.FPS_60, ExportFrameRate.FPS_120
        )
        Column {
            Text("Frame Rate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                allFrameRates.forEach { fps ->
                    FilterChip(
                        selected = settings.frameRate == fps,
                        onClick = { onSettingsChanged(settings.copy(frameRate = fps)) },
                        label = { Text(String.format(Locale.US, "%.2f", fps.floatValue).removeSuffix(".00")) }
                    )
                }
            }
        }

        // Bitrate Mode and Quality Preset
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bitrate Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ExportSettings.BitrateMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.bitrateMode == mode,
                            onClick = { onSettingsChanged(settings.copy(bitrateMode = mode)) },
                            label = { Text(mode.name) }
                        )
                    }
                }
            }
        }

        // Quality Prefix
        Column {
            Text("Quality Preset", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportQualityPreset.entries.forEach { qp ->
                    FilterChip(
                        selected = settings.qualityPreset == qp,
                        onClick = { onSettingsChanged(settings.copy(qualityPreset = qp)) },
                        label = { Text(qp.name) }
                    )
                }
            }
        }
        
        // Video Bitrate (Simplified slider/options)
        val bitratesMb = listOf(5, 10, 20, 50, 100)
        Column {
            Text("Target Bitrate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                bitratesMb.forEach { mb ->
                    val isSelected = settings.videoBitrate == mb * 1_000_000
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSettingsChanged(settings.copy(videoBitrate = mb * 1_000_000)) },
                        label = { Text("$mb Mbps") }
                    )
                }
            }
        }
    }
}
