package com.example.timeline.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.scale
import com.example.timeline.engine.preview.PreviewEngineCapabilities
import com.example.timeline.engine.preview.PreviewEngineType
import com.example.timeline.engine.preview.PreviewEngineSettings
import com.example.timeline.engine.preview.PreviewQualityMode
import com.example.timeline.engine.preview.PreviewMode

@Composable
fun ViewportSettingsMenu(
    activeEngine: PreviewEngineType,
    capabilities: PreviewEngineCapabilities,
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

            // Playback Engine
            SectionHeader("Playback Engine")
            EngineOption(PreviewEngineType.AUTO, activeEngine, onEngineSelected, enabled = true, subtext = "Defaults to Media3")
            EngineOption(PreviewEngineType.MEDIA3_FALLBACK, activeEngine, onEngineSelected, enabled = true, subtext = "Available")
            EngineOption(PreviewEngineType.BROWSER, activeEngine, onEngineSelected, enabled = true, subtext = "WebView based player")
            EngineOption(PreviewEngineType.STILL_FRAME, activeEngine, onEngineSelected, enabled = true, subtext = "Available for still preview only")
            EngineOption(PreviewEngineType.PROXY_PREVIEW, activeEngine, onEngineSelected, enabled = false, subtext = "Backend not installed")
            EngineOption(PreviewEngineType.VLC_NATIVE, activeEngine, onEngineSelected, enabled = false, subtext = "Experimental (Unavailable)")
            EngineOption(PreviewEngineType.NATIVE_CPP, activeEngine, onEngineSelected, enabled = false, subtext = "FFmpeg missing")

            Spacer(modifier = Modifier.height(16.dp))

            // Preview Quality
            SectionHeader("Preview Quality")
            QualityOption(PreviewQualityMode.AUTO, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }
            QualityOption(PreviewQualityMode.FULL, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }
            QualityOption(PreviewQualityMode.HALF, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }
            QualityOption(PreviewQualityMode.P1080, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }
            QualityOption(PreviewQualityMode.P720, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }
            QualityOption(PreviewQualityMode.P540, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }
            QualityOption(PreviewQualityMode.P360, settings.qualityMode) { onSettingsChanged(settings.copy(qualityMode = it)) }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview Mode
            SectionHeader("Preview Mode")
            ModeOption(PreviewMode.SMOOTH_PLAYBACK, settings.previewMode) { onSettingsChanged(settings.copy(previewMode = it)) }
            ModeOption(PreviewMode.COLOR_ACCURATE, settings.previewMode) { onSettingsChanged(settings.copy(previewMode = it)) }
            ModeOption(PreviewMode.GRADED_STILL, settings.previewMode) { onSettingsChanged(settings.copy(previewMode = it)) }
            ModeOption(PreviewMode.PROXY_PREVIEW, settings.previewMode) { onSettingsChanged(settings.copy(previewMode = it)) }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggles
            SectionHeader("Display Options")
            ToggleOption("Show engine badge", settings.showEngineBadge) { onSettingsChanged(settings.copy(showEngineBadge = it)) }
            ToggleOption("Show debug overlay", settings.showDebugOverlay) { onSettingsChanged(settings.copy(showDebugOverlay = it)) }
            
            SectionHeader("System Options")
            ToggleOption("Auto fallback on failure", settings.autoFallbackOnFailure) { onSettingsChanged(settings.copy(autoFallbackOnFailure = it)) }
            ToggleOption("Auto proxy for heavy media", settings.autoGenerateProxy) { onSettingsChanged(settings.copy(autoGenerateProxy = it)) }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Active Engine Status: ${activeEngine.displayName}")
            Spacer(modifier = Modifier.height(8.dp))
            
            StatusRow("Moving Playback", capabilities.canPlayMovingVideo)
            StatusRow("Realtime Grade", capabilities.canApplyRealtimeColorGrade)
            StatusRow("Proxy Support", capabilities.canUseProxy)
            StatusRow("4K Support", capabilities.supports4K)
            StatusRow("10-Bit Support", capabilities.supports10Bit)
            StatusRow("HDR Support", capabilities.supportsHDR)
            
            if (capabilities.currentFailureReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Engine Error: ${capabilities.currentFailureReason}",
                    color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (activeEngine == PreviewEngineType.VLC_NATIVE && !capabilities.canApplyRealtimeColorGrade) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: VLC Engine does not support real-time color grading. Still frame will be used.",
                    color = Color(0xFFFFA000),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
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
private fun StatusRow(label: String, supported: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White)
        Text(if (supported) "Supported" else "Not Supported", 
             color = if (supported) Color(0xFF4CAF50) else Color(0xFFE57373),
             style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
private fun EngineOption(
    engine: PreviewEngineType,
    activeEngine: PreviewEngineType,
    onSelect: (PreviewEngineType) -> Unit,
    enabled: Boolean = true,
    subtext: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect(engine) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = engine == activeEngine,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray.copy(alpha = 0.5f)
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = engine.displayName, 
                style = MaterialTheme.typography.bodyMedium, 
                color = if (enabled) Color.White else Color.Gray.copy(alpha = 0.5f)
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun QualityOption(
    quality: PreviewQualityMode,
    activeQuality: PreviewQualityMode,
    onSelect: (PreviewQualityMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(quality) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = quality == activeQuality,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(quality.displayName, style = MaterialTheme.typography.bodyMedium, color = Color.White)
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
