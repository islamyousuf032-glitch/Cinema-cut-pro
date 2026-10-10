package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.core.ProjectSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    settings: ProjectSettings?,
    isProxyActive: Boolean,
    onExportClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    onToggleScopes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow, 
                        contentDescription = "Project Settings", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    if (settings != null) {
                        Text(
                            text = settings.projectName, 
                            fontSize = 15.sp, 
                            fontWeight = FontWeight.SemiBold, 
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val resolution = "${settings.resolutionWidth}x${settings.resolutionHeight}"
                        val resLabel = when {
                            settings.resolutionWidth >= 3840 -> "4K"
                            settings.resolutionWidth >= 1920 -> "HD"
                            else -> resolution
                        }
                        
                        val rational = settings.getFpsRational()
                        val fpsStr = if (rational.denominator == 1) {
                            "${rational.numerator} FPS"
                        } else {
                            val floatFps = rational.numerator.toFloat() / rational.denominator
                            String.format("%.3f FPS", floatFps).replace(Regex("0+$"), "").removeSuffix(".")
                        }
                        
                        val colorSpaceLabel = settings.colorSpace.displayName
                        val proxyLabel = if (isProxyActive) " • PROXY" else ""
                        
                        Text(
                            text = "$resLabel • $fpsStr • $colorSpaceLabel$proxyLabel", 
                            fontSize = 11.sp, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant, 
                            fontWeight = FontWeight.Medium, 
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Text(
                            text = "Loading Project...", 
                            fontSize = 15.sp, 
                            fontWeight = FontWeight.SemiBold, 
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        actions = {
            IconButton(onClick = onToggleScopes) {
                Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = "Scopes")
            }
            IconButton(onClick = onDebugClick) {
                Icon(androidx.compose.material.icons.Icons.Filled.Info, contentDescription = "Debug")
            }
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.padding(end = 8.dp).height(32.dp)
            ) {
                Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        modifier = modifier.heightIn(min = 56.dp, max = 64.dp)
    )
}
