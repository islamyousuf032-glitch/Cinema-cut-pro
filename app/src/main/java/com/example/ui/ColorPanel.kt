package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.ui.TimelineViewModel

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.example.ui.ColorAdjustmentViewModel

@Composable
fun ColorPanel(viewModel: TimelineViewModel) {
    val colorViewModel: ColorAdjustmentViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ColorAdjustmentViewModel(viewModel) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val playheadFrame by viewModel.playheadFrame.collectAsState()
    
    val selectedClipId = uiState.selectedClipId
    val activeClip = if (selectedClipId != null) {
        uiState.project.tracks.flatMap { it.clips }.find { it.id == selectedClipId }
    } else {
        com.example.timeline.engine.preview.TimelineFrameResolver.getTopmostVisibleVideoClip(uiState.project, playheadFrame)
    }

    if (activeClip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a clip to adjust", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Temporary before/after state
    var showBefore by remember { mutableStateOf(false) }
    
    // Create a modified stack if showing before
    val displayStack = if (showBefore) activeClip.adjustments.copy(enabled = false) else activeClip.adjustments
    
    // Evaluate params immediately for sliders
    val params = displayStack.evaluateParamsAtFrame(playheadFrame)

    // Notify ViewModel when showBefore changes so it can force a preview invalidation
    LaunchedEffect(showBefore) {
        // Here we ideally send a temporary stack to the effect graph without saving to project
        val controller = viewModel.getActiveVideoClipAndAsset()
        // Just trigger standard update with fake or real stack for a moment.
        // Wait, the preview player gets its clip info from TimelinePreviewController.
        // The easiest way to force it without polluting project history is to call a specific preview function, or apply directly to project without history.
        // For now, we will toggle the enabled flag directly on the clip using a non-undoable pathway if possible, or just standard undoable.
        // Standard path:
        viewModel.updateClipAdjustments(activeClip.id, displayStack)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CLIP ADJUSTMENTS: ${activeClip.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            TextButton(
                onClick = { colorViewModel.resetAllAdjustments() },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Reset All", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { },
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            showBefore = true
                            tryAwaitRelease()
                            showBefore = false
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("Press for Before", fontSize = 10.sp)
            }
            
            Button(
                onClick = { /* Manage Presets Modal */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Presets", fontSize = 10.sp)
            }
            
            Button(
                onClick = { colorViewModel.createAdjustmentLayer() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Add Layer", fontSize = 10.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
        ) {
            com.example.ui.colorgrade.LutPanel(
                selectedLutId = params.lutId,
                intensity = params.lutIntensity,
                onLutSelected = { id -> colorViewModel.setLutId(id) },
                onIntensityChanged = { value -> colorViewModel.updateAdjustmentParam("lutIntensity", value) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Light", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParamSlider("Exposure", "exposureStops", params.exposureStops, -5f, 5f, false, colorViewModel)
            ParamSlider("Brightness", "brightness", params.brightness, -1f, 1f, false, colorViewModel)
            ParamSlider("Contrast", "contrast", params.contrast, -1f, 1f, false, colorViewModel)
            ParamSlider("Highlights", "highlights", params.highlights, -1f, 1f, false, colorViewModel)
            ParamSlider("Shadows", "shadows", params.shadows, -1f, 1f, false, colorViewModel)
            ParamSlider("Whites", "whites", params.whites, -1f, 1f, false, colorViewModel)
            ParamSlider("Blacks", "blacks", params.blacks, -1f, 1f, false, colorViewModel)
            ParamSlider("Fade", "fade", params.fade, 0f, 1f, false, colorViewModel)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Color", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParamSlider("Saturation", "saturation", params.saturation, 0f, 2f, false, colorViewModel)
            ParamSlider("Vibrance", "vibrance", params.vibrance, -1f, 1f, false, colorViewModel)
            ParamSlider("Temperature", "temperature", params.temperature, -1f, 1f, false, colorViewModel)
            ParamSlider("Tint", "tint", params.tint, -1f, 1f, false, colorViewModel)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Detail", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParamSlider("Sharpness", "sharpness", params.sharpness, 0f, 2f, false, colorViewModel)
            ParamSlider("Clarity", "clarity", params.clarity, -1f, 1f, false, colorViewModel)
            ParamSlider("Structure", "structure", params.structure, -1f, 1f, false, colorViewModel)
            ParamSlider("Dehaze", "dehaze", params.dehaze, -1f, 1f, false, colorViewModel)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Effects", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParamSlider("Grain Amount", "grainAmount", params.grainAmount, 0f, 1f, false, colorViewModel)
            ParamSlider("Grain Size", "grainSize", params.grainSize, 0f, 1f, false, colorViewModel)
            ParamSlider("Grain Roughness", "grainRoughness", params.grainRoughness, 0f, 1f, false, colorViewModel)
            ParamSlider("Vignette Amount", "vignetteAmount", params.vignetteAmount, -1f, 1f, false, colorViewModel)
            ParamSlider("Vignette Midpoint", "vignetteMidpoint", params.vignetteMidpoint, 0f, 1f, false, colorViewModel)
            ParamSlider("Vignette Feather", "vignetteFeather", params.vignetteFeather, 0f, 1f, false, colorViewModel)
            ParamSlider("Vignette Roundness", "vignetteRoundness", params.vignetteRoundness, -1f, 1f, false, colorViewModel)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        com.example.ui.colorgrade.PrimaryWheelsPanel(
            gradeParams = activeClip.colorGrade.primaryCorrections,
            onParamsChange = { newParams ->
                val newGrade = activeClip.colorGrade.copy(primaryCorrections = newParams)
                colorViewModel.updateColorGradeLive(newGrade)
            },
            onDragStart = { colorViewModel.beginAdjustmentDrag() },
            onDragEnd = { colorViewModel.endAdjustmentDrag("Color Wheels") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Curves", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))
        
        com.example.ui.colorgrade.CurvesPanel(
            curveParams = activeClip.colorGrade.curves,
            onParamsChange = { newCurves ->
                val newGrade = activeClip.colorGrade.copy(curves = newCurves)
                colorViewModel.updateColorGradeLive(newGrade)
            },
            onDragStart = { colorViewModel.beginAdjustmentDrag() },
            onDragEnd = { colorViewModel.endAdjustmentDrag("Curves") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isEyedropperActive by colorViewModel.isEyedropperActive.collectAsState()
        com.example.ui.colorgrade.HslPanel(
            params = activeClip.colorGrade.hslAdjustments,
            onParamsChange = { newHsl ->
                val newGrade = activeClip.colorGrade.copy(hslAdjustments = newHsl)
                colorViewModel.updateColorGradeLive(newGrade)
            },
            onDragStart = { colorViewModel.beginAdjustmentDrag() },
            onDragEnd = { colorViewModel.endAdjustmentDrag("HSL") },
            isEyedropperActive = isEyedropperActive,
            onToggleEyedropper = { colorViewModel.toggleEyedropper() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        com.example.ui.colorgrade.SelectiveColorPanel(
            params = activeClip.colorGrade.selectiveColor,
            onParamsChange = { newSelective ->
                val newGrade = activeClip.colorGrade.copy(selectiveColor = newSelective)
                colorViewModel.updateColorGradeLive(newGrade)
            },
            onDragStart = { colorViewModel.beginAdjustmentDrag() },
            onDragEnd = { colorViewModel.endAdjustmentDrag("Selective Color") }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        com.example.ui.colorgrade.SkinToneProtectionPanel(
            params = params,
            viewModel = colorViewModel
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Color Balance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParamSlider("Shadow Red", "shadowBalanceR", params.shadowColorBalance.x, -1f, 1f, false, colorViewModel)
            ParamSlider("Shadow Green", "shadowBalanceG", params.shadowColorBalance.y, -1f, 1f, false, colorViewModel)
            ParamSlider("Shadow Blue", "shadowBalanceB", params.shadowColorBalance.z, -1f, 1f, false, colorViewModel)
            ParamSlider("Midtone Red", "midtoneBalanceR", params.midtoneColorBalance.x, -1f, 1f, false, colorViewModel)
            ParamSlider("Midtone Green", "midtoneBalanceG", params.midtoneColorBalance.y, -1f, 1f, false, colorViewModel)
            ParamSlider("Midtone Blue", "midtoneBalanceB", params.midtoneColorBalance.z, -1f, 1f, false, colorViewModel)
            ParamSlider("Highlight Red", "highlightBalanceR", params.highlightColorBalance.x, -1f, 1f, false, colorViewModel)
            ParamSlider("Highlight Green", "highlightBalanceG", params.highlightColorBalance.y, -1f, 1f, false, colorViewModel)
            ParamSlider("Highlight Blue", "highlightBalanceB", params.highlightColorBalance.z, -1f, 1f, false, colorViewModel)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("RGB Channels", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Start))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParamSlider("Red Multiplier", "redChannelMultiplier", params.redChannelMultiplier, 0f, 2f, false, colorViewModel)
            ParamSlider("Green Multiplier", "greenChannelMultiplier", params.greenChannelMultiplier, 0f, 2f, false, colorViewModel)
            ParamSlider("Blue Multiplier", "blueChannelMultiplier", params.blueChannelMultiplier, 0f, 2f, false, colorViewModel)
            ParamSlider("Red Offset", "redOffset", params.redOffset, -1f, 1f, false, colorViewModel)
            ParamSlider("Green Offset", "greenOffset", params.greenOffset, -1f, 1f, false, colorViewModel)
            ParamSlider("Blue Offset", "blueOffset", params.blueOffset, -1f, 1f, false, colorViewModel)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ParamSlider(
    label: String,
    parameterId: String,
    value: Float,
    min: Float,
    max: Float,
    hasKeyframe: Boolean,
    colorViewModel: ColorAdjustmentViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2D31), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.20f).padding(start = 8.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val displayValue = if (parameterId == "saturation") {
                "${kotlin.math.round(value * 100f).toInt()}%"
            } else {
                String.format("%.2f", value)
            }
            Text(displayValue, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        
        Slider(
            value = value,
            onValueChange = { 
                colorViewModel.beginAdjustmentDrag()
                colorViewModel.updateAdjustmentParamLive(parameterId, it) 
            },
            onValueChangeFinished = {
                colorViewModel.endAdjustmentDrag(parameterId)
            },
            valueRange = min..max,
            modifier = Modifier.weight(0.60f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha=0.2f)
            )
        )
        
        Row(modifier = Modifier.weight(0.20f), horizontalArrangement = Arrangement.End) {
            IconButton(
                onClick = { /* Toggle Keyframe */ },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Keyframe \$label",
                    tint = if (hasKeyframe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { colorViewModel.resetAdjustmentParam(parameterId) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset \$label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
