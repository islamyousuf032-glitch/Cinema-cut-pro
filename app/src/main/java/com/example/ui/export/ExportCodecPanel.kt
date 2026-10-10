package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import com.example.timeline.export.model.ExportSettings

@Composable
fun ExportCodecPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Format & Codec", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Column {
            Text("Container / Format", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportContainer.entries.forEach { container ->
                    FilterChip(
                        selected = settings.container == container,
                        onClick = { onSettingsChanged(settings.copy(container = container)) },
                        label = { Text(container.name) }
                    )
                }
            }
        }

        Column {
            Text("Video Codec", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportCodec.entries.forEach { codec ->
                    FilterChip(
                        selected = settings.codec == codec,
                        onClick = { onSettingsChanged(settings.copy(codec = codec)) },
                        label = { Text(codec.name) }
                    )
                }
            }
        }
    }
}
