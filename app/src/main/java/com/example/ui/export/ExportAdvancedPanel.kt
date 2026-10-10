package com.example.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.export.model.ExportSettings

@Composable
fun ExportAdvancedPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    onExportLut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Advanced Options", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        ToggleRow(
            label = "Use Original Media (High Quality)",
            checked = settings.useOriginalMedia,
            onCheckedChange = { onSettingsChanged(settings.copy(useOriginalMedia = it)) }
        )
        
        ToggleRow(
            label = "Allow Proxy Export (Faster)",
            checked = settings.allowProxyExport,
            onCheckedChange = { onSettingsChanged(settings.copy(allowProxyExport = it)) }
        )

        ToggleRow(
            label = "Include Watermark",
            checked = settings.includeWatermark,
            onCheckedChange = { 
                val newWatermarkSettings = if (it && settings.watermarkSettings == null) {
                    com.example.timeline.export.model.WatermarkSettings(enabled = true)
                } else {
                    settings.watermarkSettings?.copy(enabled = it)
                }
                onSettingsChanged(settings.copy(includeWatermark = it, watermarkSettings = newWatermarkSettings)) 
            }
        )

        if (settings.includeWatermark) {
            Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val wm = settings.watermarkSettings ?: com.example.timeline.export.model.WatermarkSettings()
                
                OutlinedTextField(
                    value = wm.text,
                    onValueChange = { onSettingsChanged(settings.copy(watermarkSettings = wm.copy(text = it))) },
                    label = { Text("Watermark Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Opacity: ${(wm.opacity * 100).toInt()}%")
                Slider(
                    value = wm.opacity,
                    onValueChange = { onSettingsChanged(settings.copy(watermarkSettings = wm.copy(opacity = it))) },
                    valueRange = 0.1f..1.0f
                )

                Text("Position")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(com.example.timeline.export.model.WatermarkPosition.entries.size) { index ->
                        val pos = com.example.timeline.export.model.WatermarkPosition.entries[index]
                        FilterChip(
                            selected = wm.position == pos,
                            onClick = { onSettingsChanged(settings.copy(watermarkSettings = wm.copy(position = pos))) },
                            label = { Text(pos.name) }
                        )
                    }
                }
            }
        }

        ToggleRow(
            label = "Render Alpha Channel (Transparency)",
            checked = settings.renderAlpha,
            onCheckedChange = { onSettingsChanged(settings.copy(renderAlpha = it)) }
        )

        ToggleRow(
            label = "Use Hardware Encoder",
            checked = settings.useHardwareEncoder,
            onCheckedChange = { onSettingsChanged(settings.copy(useHardwareEncoder = it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Button(
            onClick = onExportLut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export Current Look as 3D LUT")
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
