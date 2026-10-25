package com.example.timeline.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import com.example.timeline.engine.preview.PreviewEngineSettings
import com.example.timeline.engine.preview.PreviewEngineType
import com.example.timeline.engine.preview.PreviewMode

@Composable
fun ViewportSettingsMenu(
    activeEngine: PreviewEngineType,
    settings: PreviewEngineSettings,
    onSettingsChanged: (PreviewEngineSettings) -> Unit,
    onEngineSelected: (PreviewEngineType) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        modifier = Modifier
            .padding(16.dp)
            .width(320.dp)
            .heightIn(max = 480.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Viewport Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Playback Engine")
            EngineOption(PreviewEngineType.AUTO, settings.selectedEngine, onEngineSelected, "Media3 ExoPlayer is primary for standard Android media")
            EngineOption(PreviewEngineType.MEDIA3_FALLBACK, settings.selectedEngine, onEngineSelected, "Select Media3 ExoPlayer directly")
            EngineOption(PreviewEngineType.FFMPEG_NATIVE, settings.selectedEngine, onEngineSelected, "FFmpeg demux for MP4/MOV/MKV; ProRes and APV software decode; device MediaCodec for supported H.264/HEVC/AV1/VP8/VP9, with software fallback")
            EngineOption(PreviewEngineType.VLC_NATIVE, settings.selectedEngine, onEngineSelected, "LibVLC 3.5.1 playback fallback")
            EngineOption(PreviewEngineType.BROWSER, settings.selectedEngine, onEngineSelected, "WebView-based playback")
            EngineOption(PreviewEngineType.STILL_FRAME, settings.selectedEngine, onEngineSelected, "Still-frame preview only")

            Text(
                text = "Active player: ${activeEngine.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Preview Source")
            ModeOption(PreviewMode.SMOOTH_PLAYBACK, settings.previewMode) {
                onSettingsChanged(settings.copy(previewMode = it))
            }
            ModeOption(PreviewMode.COLOR_ACCURATE, settings.previewMode) {
                onSettingsChanged(settings.copy(previewMode = it))
            }
            Text(
                text = if (settings.previewMode == PreviewMode.COLOR_ACCURATE) {
                    "Uses original media when available."
                } else {
                    "Uses a generated proxy when available; otherwise uses the original."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Automatic Actions")
            ToggleOption("Fallback after playback failure", settings.autoFallbackOnFailure) {
                onSettingsChanged(settings.copy(autoFallbackOnFailure = it))
            }
            ToggleOption("Auto-generate recommended proxies", settings.autoGenerateProxy) {
                onSettingsChanged(settings.copy(autoGenerateProxy = it))
            }

            if (com.example.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("Developer")
                ToggleOption("Show playback diagnostics", settings.showDebugOverlay) {
                    onSettingsChanged(settings.copy(showDebugOverlay = it))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close Settings")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Color.Gray, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ToggleOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

@Composable
private fun EngineOption(
    engine: PreviewEngineType,
    selectedEngine: PreviewEngineType,
    onSelect: (PreviewEngineType) -> Unit,
    subtext: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(engine) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = engine == selectedEngine,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(engine.displayName, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(subtext, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ModeOption(
    mode: PreviewMode,
    activeMode: PreviewMode,
    onSelect: (PreviewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = mode == activeMode,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(mode.displayName, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
