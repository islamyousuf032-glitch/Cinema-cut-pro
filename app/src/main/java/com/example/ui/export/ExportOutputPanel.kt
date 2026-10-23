package com.example.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.timeline.export.model.ExportSettings

@Composable
fun ExportOutputPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    onChooseFolder: () -> Unit,
    onUseDefaultFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Output", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = settings.outputFileName,
            onValueChange = { value -> onSettingsChanged(settings.copy(outputFileName = value.take(100))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Video file name") },
            supportingText = { Text("Saved as an MP4; unsafe filename characters are replaced automatically.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Text(
            text = if (settings.outputDirectoryUri.isBlank()) {
                "Default folder: Movies/CinemaCutPro"
            } else {
                "Destination: selected Android folder"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onChooseFolder, modifier = Modifier.weight(1f)) {
                Text("Choose folder")
            }
            if (settings.outputDirectoryUri.isNotBlank()) {
                TextButton(onClick = onUseDefaultFolder, modifier = Modifier.weight(1f)) {
                    Text("Use default")
                }
            }
        }
    }
}
