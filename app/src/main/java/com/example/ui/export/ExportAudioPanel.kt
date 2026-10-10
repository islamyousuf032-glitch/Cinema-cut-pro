package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.export.model.ExportAudioSettings
import com.example.timeline.export.model.ExportSettings

@Composable
fun ExportAudioPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Audio Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Column {
            Text("Audio Codec", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportAudioSettings.AudioCodec.entries.forEach { codec ->
                    FilterChip(
                        selected = settings.audioCodec == codec,
                        onClick = { onSettingsChanged(settings.copy(audioCodec = codec)) },
                        label = { Text(codec.name) }
                    )
                }
            }
        }

        Column {
            Text("Sample Rate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sampleRates = listOf(44100, 48000, 96000)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                sampleRates.forEach { sr ->
                    FilterChip(
                        selected = settings.audioSampleRate == sr,
                        onClick = { onSettingsChanged(settings.copy(audioSampleRate = sr)) },
                        label = { Text("${sr/1000} kHz") }
                    )
                }
            }
        }

        Column {
            Text("Channels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val channels = listOf(Pair(1, "Mono"), Pair(2, "Stereo"), Pair(6, "5.1 Surround"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                channels.forEach { ch ->
                    FilterChip(
                        selected = settings.audioChannels == ch.first,
                        onClick = { onSettingsChanged(settings.copy(audioChannels = ch.first)) },
                        label = { Text(ch.second) }
                    )
                }
            }
        }
        
        Column {
            Text("Bitrate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bitrates = listOf(128_000, 192_000, 256_000, 320_000)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                bitrates.forEach { br ->
                    FilterChip(
                        selected = settings.audioBitrate == br,
                        onClick = { onSettingsChanged(settings.copy(audioBitrate = br)) },
                        label = { Text("${br/1000} kbps") }
                    )
                }
            }
        }
    }
}
