package com.example.ui.editor.viewport

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MediaAvailabilityStatus
import com.example.timeline.media.MediaErrorStatus
import com.example.timeline.media.PreviewStatus
import com.example.timeline.media.ProxyStatus

@Composable
fun ViewportStatusCard(
    currentAsset: MediaAsset?,
    playerError: String?,
    engineType: String,
    onRequestProxy: (String) -> Unit,
    onRequestRelink: (String) -> Unit,
    onRetryPlayback: () -> Unit,
    onSwitchEngine: (com.example.timeline.engine.preview.PreviewEngineType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentAsset == null && playerError == null) return

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (currentAsset != null) {
            if (currentAsset.proxyStatus == ProxyStatus.GENERATING) {
                Text("Proxy Generating...", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.bodySmall)
            } else if (currentAsset.proxyStatus == ProxyStatus.REQUIRED) {
                Text("Proxy Required", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.bodySmall)
            } else if (currentAsset.proxyStatus == ProxyStatus.FAILED) {
                Text("Proxy Failed", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.bodySmall)
            }

            if (currentAsset.mediaErrorStatus != MediaErrorStatus.NONE) {
                Text("Media Error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.bodySmall)
            } else if (currentAsset.mediaAvailabilityStatus == MediaAvailabilityStatus.MISSING_NEEDS_RELINK) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                    Text("Media Offline", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onRequestRelink(currentAsset.assetId) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Relink") }
                }
            }

            val hasProxy = currentAsset.proxyStatus == ProxyStatus.READY
            if (currentAsset.previewStatus == PreviewStatus.DIRECT_FAILED_RUNTIME && !hasProxy) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                    Text("Direct Playback Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onRequestProxy(currentAsset.assetId) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Generate Proxy") }
                }
            }
        }
        
        if (playerError != null) {
            val msg = if (currentAsset?.proxyStatus == ProxyStatus.READY) "Proxy Playback Error: $playerError" else "$engineType Playback Error: $playerError"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onRetryPlayback, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Retry") }
                
                if (engineType == "Native VLC") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onSwitchEngine(com.example.timeline.engine.preview.PreviewEngineType.MEDIA3_FALLBACK) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Use Media3") }
                } else if (engineType == "Media3" || engineType == "Auto") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onSwitchEngine(com.example.timeline.engine.preview.PreviewEngineType.STILL_FRAME) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Fallback to Still") }
                }
            }
        }
    }
}
