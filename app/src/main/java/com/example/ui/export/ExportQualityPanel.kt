package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

        val resOptions = ExportSettingsDefaults.resolutionOptions(settings.resolutionWidth, settings.resolutionHeight)
        Column {
            Text("Resolution", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Output orientation is preserved; scaling fits the image without cropping.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                resOptions.forEach { option ->
                    FilterChip(
                        selected = settings.resolutionWidth == option.width && settings.resolutionHeight == option.height,
                        onClick = {
                            val updated = settings.copy(resolutionWidth = option.width, resolutionHeight = option.height)
                            val presetBitrate = ExportSettingsDefaults.bitrateForQualityPreset(
                                updated.qualityPreset,
                                option.width,
                                option.height,
                                updated.frameRate
                            )
                            onSettingsChanged(
                                if (presetBitrate == null) updated
                                else updated.copy(videoBitrate = presetBitrate, maxBitrate = presetBitrate)
                            )
                        },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Frame Rate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${String.format(Locale.US, "%.3f", settings.frameRate.floatValue)} fps — source rate is preserved. Mixed or variable frame rates are rejected because conversion is not implemented yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column {
            Text("Bitrate Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(ExportSettings.BitrateMode.VBR, ExportSettings.BitrateMode.CBR).forEach { mode ->
                    FilterChip(
                        selected = settings.bitrateMode == mode,
                        onClick = { onSettingsChanged(settings.copy(bitrateMode = mode)) },
                        label = { Text(mode.name) }
                    )
                }
            }
        }

        Column {
            Text("Quality Preset", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportQualityPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.qualityPreset == preset,
                        onClick = {
                            val updated = settings.copy(qualityPreset = preset)
                            val presetBitrate = ExportSettingsDefaults.bitrateForQualityPreset(
                                preset,
                                updated.resolutionWidth,
                                updated.resolutionHeight,
                                updated.frameRate
                            )
                            onSettingsChanged(
                                if (presetBitrate == null) updated
                                else updated.copy(videoBitrate = presetBitrate, maxBitrate = presetBitrate)
                            )
                        },
                        label = { Text(preset.name) }
                    )
                }
            }
        }

        val bitratesMbps = listOf(5, 10, 20, 50, 100)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Target Video Bitrate · ${String.format(Locale.US, "%.1f", settings.videoBitrate / 1_000_000f)} Mbps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                bitratesMbps.forEach { mbps ->
                    val bitrate = mbps * 1_000_000
                    FilterChip(
                        selected = settings.videoBitrate == bitrate,
                        onClick = {
                            onSettingsChanged(
                                settings.copy(
                                    videoBitrate = bitrate,
                                    maxBitrate = bitrate,
                                    qualityPreset = ExportQualityPreset.CUSTOM
                                )
                            )
                        },
                        label = { Text("$mbps Mbps") }
                    )
                }
            }
        }
    }
}
