package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Advanced Options", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            "The current real export path reads original media and uses Android H.264/MP4. " +
                "Proxy export, alpha, watermark compositing, and audio normalization are not implemented.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Keyframe interval", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Controls the requested H.264 I-frame interval in seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(1, 2, 4, 8).forEach { seconds ->
                    FilterChip(
                        selected = settings.keyframeInterval == seconds,
                        onClick = { onSettingsChanged(settings.copy(keyframeInterval = seconds)) },
                        label = { Text("${seconds}s") }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(onClick = onExportLut, modifier = Modifier.fillMaxWidth()) {
            Text("Export Current Look as 3D LUT")
        }
    }
}
