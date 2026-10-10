package com.example.ui.colorgrade

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.colorgrade.ColorWheelParams
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun ColorWheelControl(
    title: String,
    params: ColorWheelParams,
    onVectorChange: (Float, Float, Float) -> Unit,
    onLumaChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    var showNumericInput by remember { mutableStateOf(false) }

    if (showNumericInput) {
        var rStr by remember { mutableStateOf(params.r.toString()) }
        var gStr by remember { mutableStateOf(params.g.toString()) }
        var bStr by remember { mutableStateOf(params.b.toString()) }
        AlertDialog(
            onDismissRequest = { showNumericInput = false },
            title = { Text("Set Color Vector") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rStr, onValueChange = { rStr = it },
                        label = { Text("Red") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = gStr, onValueChange = { gStr = it },
                        label = { Text("Green") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = bStr, onValueChange = { bStr = it },
                        label = { Text("Blue") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDragStart()
                    onVectorChange(
                        rStr.toFloatOrNull() ?: params.r,
                        gStr.toFloatOrNull() ?: params.g,
                        bStr.toFloatOrNull() ?: params.b
                    )
                    onDragEnd()
                    showNumericInput = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showNumericInput = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1F22), shape = MaterialTheme.shapes.medium)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            IconButton(onClick = onReset, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset $title",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color Wheel
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .aspectRatio(1f)
            ) {
                    val renderer = remember { ColorWheelRenderer() }
                    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    
                    val wheelSizePx = with(LocalDensity.current) { 160.dp.toPx().toInt() }
                    LaunchedEffect(wheelSizePx, renderer.activeMode) {
                        if (renderer.activeMode != ColorWheelRenderMode.COMPOSE_SWEEP_GRADIENT) {
                            val androidBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                renderer.generateBitmap(wheelSizePx)
                            }
                            bitmap = androidBitmap.asImageBitmap()
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        
                        if (renderer.activeMode == ColorWheelRenderMode.COMPOSE_SWEEP_GRADIENT) {
                            val colors = listOf(
                                Color.Red, Color.Magenta, Color.Blue, 
                                Color.Cyan, Color.Green, Color.Yellow, Color.Red
                            )
                            drawCircle(
                                brush = Brush.sweepGradient(colors, center = center),
                                radius = radius,
                                center = center,
                                alpha = 0.3f
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, Color.Transparent),
                                    center = center,
                                    radius = radius
                                ),
                                radius = radius,
                                center = center,
                                alpha = 0.8f
                            )
                        } else {
                            bitmap?.let {
                                val topLeft = Offset(center.x - radius, center.y - radius)
                                drawImage(it, topLeft = topLeft, alpha = 0.3f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White, Color.Transparent),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center,
                                    alpha = 0.8f
                                )
                            }
                        }

                        // Border
                        drawCircle(
                            color = Color.DarkGray,
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                // Draggable Puck
                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var longPressJob: Job? = scope.launch {
                            delay(viewConfiguration.longPressTimeoutMillis)
                            showNumericInput = true
                        }
                        
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height).toFloat() / 2f
                        
                        var dx = down.position.x - center.x
                        var dy = down.position.y - center.y
                        var dist = hypot(dx, dy)
                        
                        onDragStart()
                        var isCenterTap = dist < radius * 0.15f
                        if (isCenterTap) {
                            onVectorChange(0f, 0f, 0f)
                        } else {
                            if (dist > radius) {
                                dx = dx / dist * radius
                                dy = dy / dist * radius
                                dist = radius
                            }
                            val angle = atan2(dy, dx)
                            val intensity = dist / radius
                            onVectorChange(
                                (cos(angle) * intensity).toFloat(),
                                (cos(angle + 2.0944f) * intensity).toFloat(),
                                (cos(angle + 4.1888f) * intensity).toFloat()
                            )
                        }
                        
                        var pointer = down
                        while (true) {
                            val event = awaitPointerEvent()
                            pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) break
                            
                            val distMoved = (pointer.position - down.position).getDistance()
                            if (distMoved > viewConfiguration.touchSlop) {
                                longPressJob?.cancel()
                                longPressJob = null
                            }
                            
                            pointer.consume()
                            
                            dx = pointer.position.x - center.x
                            dy = pointer.position.y - center.y
                            dist = hypot(dx, dy)
                            
                            if (isCenterTap && dist > radius * 0.15f) {
                                isCenterTap = false
                            }
                            
                            if (!isCenterTap) {
                                if (dist > radius) {
                                    dx = dx / dist * radius
                                    dy = dy / dist * radius
                                    dist = radius
                                }
                                val angle = atan2(dy, dx)
                                val intensity = dist / radius
                                onVectorChange(
                                    (cos(angle) * intensity).toFloat(),
                                    (cos(angle + 2.0944f) * intensity).toFloat(),
                                    (cos(angle + 4.1888f) * intensity).toFloat()
                                )
                            }
                        }
                        longPressJob?.cancel()
                        onDragEnd()
                    }
                }) {
                    val maxRadiusPx = LocalDensity.current.run { 80.dp.toPx() }
                    val centerPx = maxRadiusPx
                    
                    val x = (params.r * cos(0f) + params.g * cos(2.0944f) + params.b * cos(4.1888f)) / 1.5f
                    val y = (params.r * sin(0f) + params.g * sin(2.0944f) + params.b * sin(4.1888f)) / -1.5f
                    val px = (x * maxRadiusPx).coerceIn(-maxRadiusPx, maxRadiusPx) + centerPx
                    val py = (y * maxRadiusPx).coerceIn(-maxRadiusPx, maxRadiusPx) + centerPx

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(LocalDensity.current) { px.toDp() - 8.dp },
                                y = with(LocalDensity.current) { py.toDp() - 8.dp }
                            )
                            .size(16.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, Color.Black, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Luminance Slider / Dial
            ColorControlSlider(
                label = "Luma",
                value = params.luma,
                range = -1f..1f,
                onValueChange = {
                    onLumaChange(it)
                },
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                onReset = { onLumaChange(0f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
