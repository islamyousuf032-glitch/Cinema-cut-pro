package com.example.ui.transform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TransformControlRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isKeyframed: Boolean = false,
    hasAnyKeyframes: Boolean = false,
    onKeyframeToggle: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )

            val keyframeColor = if (isKeyframed) Color.Red else if (hasAnyKeyframes) Color.Gray else MaterialTheme.colorScheme.outline
            IconButton(onClick = onKeyframeToggle, modifier = Modifier.size(32.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(keyframeColor, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
