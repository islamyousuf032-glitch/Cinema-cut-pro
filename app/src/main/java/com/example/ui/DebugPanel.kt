package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeline.media.MediaAsset

@Composable
fun DebugPanel(
    assets: List<MediaAsset>,
    clipsCount: Int,
    playheadFrame: Long,
    diagnostics: Map<String, String> = emptyMap(),
    onTestVlc: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        Text("DEBUG PANEL", color = Color.Red, style = MaterialTheme.typography.labelSmall)
        Text("Media Assets: ${assets.size} | Timeline Clips: $clipsCount | Playhead: $playheadFrame", color = Color.White, style = MaterialTheme.typography.bodySmall)
        
        Spacer(modifier = Modifier.height(4.dp))
        
        diagnostics.forEach { (key, value) ->
            Text("$key = $value", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LazyColumn {
            items(assets) { asset ->
                Column(modifier = Modifier.padding(vertical = 4.dp).background(Color.DarkGray).padding(4.dp).fillMaxWidth()) {
                    Text("ID: ${asset.assetId}", color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
                    Text("Name: ${asset.displayName}", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    
                    val metadata = asset.metadata
                    val videoStream = metadata.videoStreams.firstOrNull()
                    
                    Text("Dimensions: ${metadata.width}x${metadata.height}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text("MIME: ${asset.mimeType}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    
                    if (videoStream != null) {
                        Text("Codec: ${videoStream.codecName} Profile: ${videoStream.codecProfile} Level: ${videoStream.codecLevel}", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                        Text("Color Standard: ${videoStream.colorStandard} Transfer: ${videoStream.colorTransfer} Range: ${videoStream.colorRange}", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                    }
                    
                    Text("Preview: ${asset.previewStatus} | Proxy: ${asset.proxyStatus} | Error: ${asset.mediaErrorStatus}", color = Color.Green, style = MaterialTheme.typography.labelSmall)
                    Text("Avail: ${asset.mediaAvailabilityStatus}", color = Color.Magenta, style = MaterialTheme.typography.labelSmall)
                    
                    if (onTestVlc != null && asset.localOriginalUriString != null) {
                        androidx.compose.material3.Button(onClick = { onTestVlc(asset.localOriginalUriString!!) }) {
                            Text("Test VLC")
                        }
                    }
                }
            }
        }
    }
}
