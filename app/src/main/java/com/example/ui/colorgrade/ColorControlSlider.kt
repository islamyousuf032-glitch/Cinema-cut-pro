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
    modifier: Modifier = Modifier,
    valueFormatter: ((Float) -> String)? = null
) {
    var dragValue by remember { mutableFloatStateOf(value) }
    val latestValue = rememberUpdatedState(value)
    val latestRange = rememberUpdatedState(range)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestFormatter = rememberUpdatedState(valueFormatter)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val displayVal = latestFormatter.value?.invoke(value) ?: if (range.start < 0f) {
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
                .height(44.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragValue = latestValue.value
                            latestOnDragStart.value()
                        },
                        onDragEnd = { latestOnDragEnd.value() },
                        onDragCancel = { latestOnDragEnd.value() }
                    ) { change, dragAmount ->
                        change.consume()
                        val currentRange = latestRange.value
                        val sensitivity = (currentRange.endInclusive - currentRange.start) / 500f
                        dragValue = (dragValue + dragAmount * sensitivity).coerceIn(currentRange)
                        latestOnValueChange.value(dragValue)
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
                val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
                val fraction = ((value - range.start) / span).coerceIn(0f, 1f)

                if (range.start < 0f && range.endInclusive > 0f) {
                    val center = (0f - range.start) / span
                    val width = kotlin.math.abs(fraction - center)
                    val start = minOf(fraction, center)
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
                                .weight(fraction.coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.weight((1f - fraction).coerceAtLeast(0.001f)))
                    }
                }
            }
        }
    }
}
