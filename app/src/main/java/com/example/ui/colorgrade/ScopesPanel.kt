package com.example.ui.colorgrade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.model.scopes.ScopeData

enum class ScopeType {
    LUMA_WAVEFORM, RGB_PARADE, VECTORSCOPE, HISTOGRAM
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScopesPanel(
    scopeData: ScopeData,
    isAnalyzing: Boolean = false,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedScope by remember { mutableStateOf(ScopeType.LUMA_WAVEFORM) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SCOPES", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (isAnalyzing) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close scopes", tint = Color.LightGray)
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ScopeType.values().forEach { type ->
                    val label = when (type) {
                        ScopeType.LUMA_WAVEFORM -> "Luma Waveform"
                        ScopeType.RGB_PARADE -> "RGB Parade"
                        ScopeType.VECTORSCOPE -> "Vectorscope"
                        ScopeType.HISTOGRAM -> "Histogram"
                    }
                    FilterChip(
                        selected = selectedScope == type,
                        onClick = { selectedScope = type },
                        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = Color.LightGray,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.Black)) {
                when (selectedScope) {
                    ScopeType.LUMA_WAVEFORM -> LumaWaveformCanvas(scopeData, Modifier.fillMaxSize())
                    ScopeType.RGB_PARADE -> RgbParadeCanvas(scopeData, Modifier.fillMaxSize())
                    ScopeType.VECTORSCOPE -> VectorscopeCanvas(scopeData, Modifier.fillMaxSize())
                    ScopeType.HISTOGRAM -> HistogramCanvas(scopeData, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun LumaWaveformCanvas(data: ScopeData, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cw = w / 256f
        val ch = h / 256f
        
        // Draw grid
        drawLine(Color.DarkGray, Offset(0f, h/2), Offset(w, h/2), strokeWidth = 1f)
        drawLine(Color.DarkGray, Offset(0f, h*0.25f), Offset(w, h*0.25f), strokeWidth = 1f)
        drawLine(Color.DarkGray, Offset(0f, h*0.75f), Offset(w, h*0.75f), strokeWidth = 1f)
        
        val path = Path()
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                val intensity = data.waveformLuma[x * 256 + y]
                if (intensity > 0) {
                    val px = x * cw
                    val py = h - (y * ch)
                    val alpha = (intensity / 5f).coerceIn(0.2f, 1f)
                    val color = if (y >= 254 || y <= 1) Color.Red else Color.White
                    drawRect(color.copy(alpha = alpha), topLeft = Offset(px, py), size = androidx.compose.ui.geometry.Size(cw, ch))
                }
            }
        }
    }
}

@Composable
fun RgbParadeCanvas(data: ScopeData, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cw = (w / 3f) / 256f
        val ch = h / 256f
        
        val colors = listOf(data.paradeR to Color.Red, data.paradeG to Color.Green, data.paradeB to Color.Blue)
        
        for (i in 0..2) {
            val offsetBase = i * (w / 3f)
            val channel = colors[i].first
            val drawColor = colors[i].second
            for (x in 0 until 256) {
                for (y in 0 until 256) {
                    val intensity = channel[x * 256 + y]
                    if (intensity > 0) {
                        val px = offsetBase + x * cw
                        val py = h - (y * ch)
                        val alpha = (intensity / 5f).coerceIn(0.2f, 1f)
                        drawRect(drawColor.copy(alpha = alpha), topLeft = Offset(px, py), size = androidx.compose.ui.geometry.Size(cw, ch))
                    }
                }
            }
        }
    }
}

@Composable
fun VectorscopeCanvas(data: ScopeData, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = Math.min(w, h) / 2f
        
        drawCircle(Color.DarkGray, radius, Offset(cx, cy), style = Stroke(1f))
        drawLine(Color.DarkGray, Offset(cx, cy - radius), Offset(cx, cy + radius), strokeWidth = 1f)
        drawLine(Color.DarkGray, Offset(cx - radius, cy), Offset(cx + radius, cy), strokeWidth = 1f)
        
        // Skin tone line approx angle 123 deg or similar in vectorscope, but it is standard -I axis
        // Let's draw a simple line upper left quadrant roughly
        drawLine(Color(0xFFFFAA88).copy(alpha=0.5f), Offset(cx, cy), Offset(cx - radius * 0.4f, cy - radius * 0.4f), strokeWidth = 2f)

        val cw = w / 256f
        val ch = h / 256f
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                val intensity = data.vectorscope[x * 256 + y]
                if (intensity > 0) {
                    val alpha = (intensity / 5f).coerceIn(0.2f, 1f)
                    val px = x * cw
                    val py = (255 - y) * ch // Flip Y 
                    drawRect(Color.White.copy(alpha = alpha), topLeft = Offset(px, py), size = androidx.compose.ui.geometry.Size(cw, ch))
                }
            }
        }
    }
}

@Composable
fun HistogramCanvas(data: ScopeData, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cw = w / 256f
        
        var maxValOrig = 1
        for (i in 0 until 256) {
            maxValOrig = maxOf(maxValOrig, data.histogramLuma[i])
        }
        // Use a logarithmic or clamped scale so spikes don't crush the rest
        val maxVal = maxOf(maxValOrig / 4f, 1f) // heuristic

        for (x in 0 until 256) {
            val R = (data.histogramR[x] / maxVal).coerceIn(0f, 1f) * h
            val G = (data.histogramG[x] / maxVal).coerceIn(0f, 1f) * h
            val B = (data.histogramB[x] / maxVal).coerceIn(0f, 1f) * h
            val L = (data.histogramLuma[x] / maxVal).coerceIn(0f, 1f) * h
            
            val px = x * cw
            
            drawRect(Color.White.copy(alpha=0.4f), topLeft = Offset(px, h - L), size = androidx.compose.ui.geometry.Size(cw, L))
            drawRect(Color.Red.copy(alpha=0.3f), topLeft = Offset(px, h - R), size = androidx.compose.ui.geometry.Size(cw, R))
            drawRect(Color.Green.copy(alpha=0.3f), topLeft = Offset(px, h - G), size = androidx.compose.ui.geometry.Size(cw, G))
            drawRect(Color.Blue.copy(alpha=0.3f), topLeft = Offset(px, h - B), size = androidx.compose.ui.geometry.Size(cw, B))
        }
    }
}
