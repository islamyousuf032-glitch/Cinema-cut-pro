package com.example.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExportAdvancedPanel(
    onExportLut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Advanced Options", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            "The current real export path reads original media and uses an Android H.264 encoder " +
                "(hardware acceleration is best-effort). Proxy export, alpha, watermark compositing, " +
                "and audio normalization are not implemented, so those switches are intentionally " +
                "not offered here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Button(onClick = onExportLut, modifier = Modifier.fillMaxWidth()) {
            Text("Export Current Look as 3D LUT")
        }
    }
}
