package com.example.ui.colorgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ColorControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dragAccumulator by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val displayVal = if (range.start < 0f) {
                ((value / range.endInclusive) * 100).roundToInt().toString()
            } else {
                ((value - range.start) / (range.endInclusive - range.start) * 100).roundToInt().toString()
            }
            Text(displayVal, color = Color.White, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp) // Minimum touch target size
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { 
                            dragAccumulator = 0f
                            onDragEnd()
                        },
                        onDragCancel = { 
                            dragAccumulator = 0f
                            onDragEnd()
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        val rangeSpan = range.endInclusive - range.start
                        val sensitivity = rangeSpan / 500f 
                        val newValue = (value + dragAmount * sensitivity).coerceIn(range)
                        onValueChange(newValue)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C30))
            ) {
                val fraction = if (range.start < 0f) {
                (value - range.start) / (range.endInclusive - range.start)
            } else {
                (value - range.start) / (range.endInclusive - range.start)
            }
            val safeFraction = fraction.coerceIn(0f, 1f)

            // Center-based logic for bipolar sliders
            if (range.start < 0f && range.endInclusive > 0f) {
                val center = (0f - range.start) / (range.endInclusive - range.start)
                val width = kotlin.math.abs(safeFraction - center)
                val start = minOf(safeFraction, center)
                Row(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(start.coerceAtLeast(0.001f)))
                    Box(
                        modifier = Modifier
                            .weight(width.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(Color.Gray)
                    )
                    val remaining = 1f - (start + width)
                    Spacer(modifier = Modifier.weight(remaining.coerceAtLeast(0.001f)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(Color.White)
                        .align(Alignment.Center)
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(safeFraction.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(Color.Gray)
                    )
                    val remaining = 1f - safeFraction
                    Spacer(modifier = Modifier.weight(remaining.coerceAtLeast(0.001f)))
                }
            }
        }
        }
    }
}
