package com.example.ui.colorgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.colorgrade.HslQualifierParams

@Composable
fun HslPanel(
    params: HslQualifierParams,
    onParamsChange: (HslQualifierParams) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isEyedropperActive: Boolean = false,
    onToggleEyedropper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HSL Adjustments", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onToggleEyedropper, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = "Eyedropper", 
                        tint = if (isEyedropperActive) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
            Switch(
                checked = params.enabled,
                onCheckedChange = { onParamsChange(params.copy(enabled = it)) }
            )
        }

        if (params.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = params.showMatte, onCheckedChange = { onParamsChange(params.copy(showMatte = it)) })
                Text("Show Mask Matte", color = Color.LightGray, fontSize = 12.sp)
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Checkbox(checked = params.invertMask, onCheckedChange = { onParamsChange(params.copy(invertMask = it)) })
                Text("Invert Mask", color = Color.LightGray, fontSize = 12.sp)
            }

            Text("Qualifier Ranges", color = Color.LightGray, fontSize = 14.sp)
            
            ColorControlSlider("Hue Center", params.hueCenter, 0f..1f, { onParamsChange(params.copy(hueCenter = it)) }, { onParamsChange(params.copy(hueCenter = 0.5f)) })
            ColorControlSlider("Hue Width", params.hueWidth, 0f..1f, { onParamsChange(params.copy(hueWidth = it)) }, { onParamsChange(params.copy(hueWidth = 0.1f)) })
            ColorControlSlider("Hue Feather", params.hueFeather, 0f..1f, { onParamsChange(params.copy(hueFeather = it)) }, { onParamsChange(params.copy(hueFeather = 0.1f)) })
            
            ColorControlSlider("Sat Min", params.saturationMin, 0f..1f, { onParamsChange(params.copy(saturationMin = it)) }, { onParamsChange(params.copy(saturationMin = 0f)) })
            ColorControlSlider("Sat Max", params.saturationMax, 0f..1f, { onParamsChange(params.copy(saturationMax = it)) }, { onParamsChange(params.copy(saturationMax = 1f)) })
            ColorControlSlider("Sat Feather", params.saturationFeather, 0f..1f, { onParamsChange(params.copy(saturationFeather = it)) }, { onParamsChange(params.copy(saturationFeather = 0.1f)) })

            ColorControlSlider("Luma Min", params.luminanceMin, 0f..1f, { onParamsChange(params.copy(luminanceMin = it)) }, { onParamsChange(params.copy(luminanceMin = 0f)) })
            ColorControlSlider("Luma Max", params.luminanceMax, 0f..1f, { onParamsChange(params.copy(luminanceMax = it)) }, { onParamsChange(params.copy(luminanceMax = 1f)) })
            ColorControlSlider("Luma Feather", params.luminanceFeather, 0f..1f, { onParamsChange(params.copy(luminanceFeather = it)) }, { onParamsChange(params.copy(luminanceFeather = 0.1f)) })

            Spacer(modifier = Modifier.height(16.dp))
            Text("Adjustments Inside Mask", color = Color.LightGray, fontSize = 14.sp)

            ColorControlSlider("Hue Shift", params.hueShift, -0.5f..0.5f, { onParamsChange(params.copy(hueShift = it)) }, { onParamsChange(params.copy(hueShift = 0f)) })
            ColorControlSlider("Saturation", params.saturation, 0f..2f, { onParamsChange(params.copy(saturation = it)) }, { onParamsChange(params.copy(saturation = 1f)) })
            ColorControlSlider("Luminance", params.luminance, -1f..1f, { onParamsChange(params.copy(luminance = it)) }, { onParamsChange(params.copy(luminance = 0f)) })
            ColorControlSlider("Contrast", params.contrast, -1f..1f, { onParamsChange(params.copy(contrast = it)) }, { onParamsChange(params.copy(contrast = 0f)) })
            ColorControlSlider("Temperature", params.temperature, -1f..1f, { onParamsChange(params.copy(temperature = it)) }, { onParamsChange(params.copy(temperature = 0f)) })
            ColorControlSlider("Tint", params.tint, -1f..1f, { onParamsChange(params.copy(tint = it)) }, { onParamsChange(params.copy(tint = 0f)) })
        }
    }
}

@Composable
fun HslParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    defaultValue: Float = 0f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.25f)) {
            Text(label, fontSize = 10.sp, color = Color.LightGray)
            Text(String.format("%.2f", value), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
        
        Slider(
            value = value,
            onValueChange = { 
                onDragStart()
                onValueChange(it) 
            },
            onValueChangeFinished = {
                onDragEnd()
            },
            valueRange = min..max,
            modifier = Modifier.weight(0.65f)
        )
        
        IconButton(
            onClick = { onValueChange(defaultValue) },
            modifier = Modifier.weight(0.1f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}
