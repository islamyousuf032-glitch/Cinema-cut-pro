package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.export.model.ExportColorSettings
import com.example.timeline.export.model.ExportSettings

@Composable
fun ExportColorPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Color Space & HDR", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Export as HDR", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.exportHDR,
                onCheckedChange = { isHdr -> 
                    // Update color space if HDR is toggled to match
                    val newColorOutput = if (isHdr) {
                        if (settings.colorOutput != ExportColorSettings.ColorOutput.HLG && settings.colorOutput != ExportColorSettings.ColorOutput.PQ) {
                            ExportColorSettings.ColorOutput.HLG
                        } else settings.colorOutput
                    } else {
                        ExportColorSettings.ColorOutput.Rec709
                    }
                    onSettingsChanged(settings.copy(exportHDR = isHdr, colorOutput = newColorOutput)) 
                }
            )
        }

        Column {
            Text("Color Space", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportColorSettings.ColorOutput.entries.forEach { co ->
                    FilterChip(
                        selected = settings.colorOutput == co,
                        onClick = { onSettingsChanged(settings.copy(colorOutput = co)) },
                        label = { Text(co.name) }
                    )
                }
            }
        }

        Column {
            Text("Tone Mapping", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportColorSettings.ToneMappingMode.entries.forEach { tm ->
                    FilterChip(
                        selected = settings.toneMappingMode == tm,
                        onClick = { onSettingsChanged(settings.copy(toneMappingMode = tm)) },
                        label = { Text(tm.name.replace("_", " ")) }
                    )
                }
            }
        }
    }
}
