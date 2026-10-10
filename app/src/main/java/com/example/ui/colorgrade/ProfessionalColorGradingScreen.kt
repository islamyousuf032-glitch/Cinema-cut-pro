package com.example.ui.colorgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.colorgrade.ColorGradeStack
import com.example.timeline.ui.TimelineViewModel
import com.example.ui.ColorAdjustmentViewModel
import com.example.ui.ColorMatchViewModel
import com.example.ui.ColorLayerViewModel

import com.example.timeline.engine.preview.TimelinePreviewController
import com.example.timeline.engine.preview.PreviewEngineType

import androidx.compose.material.icons.automirrored.filled.List

@Composable
fun ProfessionalColorGradingScreen(
    timelineViewModel: TimelineViewModel,
    colorMatchViewModel: ColorMatchViewModel,
    scopeViewModel: com.example.ui.scopes.ScopeViewModel,
    previewController: TimelinePreviewController
) {
    val colorViewModel: ColorAdjustmentViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ColorAdjustmentViewModel(timelineViewModel) as T
            }
        }
    )

    
    val layerViewModel: ColorLayerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ColorLayerViewModel(timelineViewModel) as T
            }
        }
    )

    val uiState by timelineViewModel.uiState.collectAsState()
    val playheadFrame by timelineViewModel.playheadFrame.collectAsState()

    val activeClip = if (uiState.selectedClipId != null) {
        uiState.project.tracks.flatMap { it.clips }.find { it.id == uiState.selectedClipId }
    } else {
        com.example.timeline.engine.GradingFrameResolver.resolve(uiState.project, playheadFrame)?.first
    }

    if (activeClip == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E22)), contentAlignment = Alignment.Center) {
            Text("Select a clip to grade", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    
    val layerStack = activeClip.colorLayers
    val selectedLayerId by layerViewModel.selectedLayerId.collectAsState()
    val activeLayer = layerStack.layers.find { it.id == selectedLayerId } ?: layerStack.layers.lastOrNull()
    val currentGrade = activeLayer?.grade ?: com.example.model.colorgrade.ColorGradeHelper.defaultColorGrade(activeClip.id)

    var showBefore by remember { mutableStateOf(false) }
    
    // Check Engine capabilities
    val engineType by previewController.engineTypeFlow.collectAsState()
    val capabilities = previewController.previewEngine.capabilities
    
    var showEnginePrompt by remember { mutableStateOf(false) }
    LaunchedEffect(engineType) {
        if (!capabilities.canApplyRealtimeColorGrade) {
            showEnginePrompt = true
        } else {
            showEnginePrompt = false
        }
    }
    var showPresets by remember { mutableStateOf(false) }
    var showNodes by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Wheels", "Curves", "HSL", "LUT", "Match", "Scopes")

    val displayStack = if (showBefore) activeClip.adjustments.copy(enabled = false) else activeClip.adjustments
    val params = displayStack.evaluateParamsAtFrame(playheadFrame)

    LaunchedEffect(showBefore) {
        timelineViewModel.updateClipAdjustments(activeClip.id, displayStack)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF16161A))
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E22))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("COLOR GRADE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Text(activeClip.name, fontSize = 14.sp, color = Color.White)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showNodes = !showNodes },
                        colors = ButtonDefaults.buttonColors(containerColor = if (showNodes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Text("Nodes", fontSize = 12.sp, color = if (showNodes) MaterialTheme.colorScheme.onPrimary else Color.LightGray)
                    }

                    Button(
                        onClick = { showPresets = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Presets", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Presets", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = { showBefore = !showBefore },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Text(if (showBefore) "Show After" else "Show Before", fontSize = 12.sp)
                    }

                    // Copy/Paste
                    IconButton(onClick = { colorViewModel.copyAdjustmentsFromSelectedClip() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Copy") // TODO: better icon
                    }
                    IconButton(onClick = { colorViewModel.pasteAdjustmentsToSelectedClip() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Paste") // TODO: better icon
                    }
                    
                    IconButton(onClick = { colorViewModel.resetAllAdjustments() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset All", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Capability-aware Engine Prompt
            if (!capabilities.canApplyRealtimeColorGrade) {
                Surface(
                    color = Color(0xFF3E2723), 
                    modifier = Modifier.fillMaxWidth().padding(8.dp), 
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            val msg = "Realtime grading not available on this engine"
                            Text(msg, style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        
                        // Adaptive buttons
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (engineType != PreviewEngineType.MEDIA3_FALLBACK) {
                                Button(onClick = { previewController.setEngineType(PreviewEngineType.MEDIA3_FALLBACK) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                    Text("Media3 Color Preview", fontSize = 10.sp)
                                }
                            }

                            if (engineType != PreviewEngineType.STILL_FRAME) {
                                Button(onClick = { previewController.setEngineType(PreviewEngineType.STILL_FRAME) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                    Text("Graded Still Preview", fontSize = 10.sp)
                                }
                            }

                            Button(onClick = { timelineViewModel.generateProxy(activeClip.mediaId) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                                Text("Generate Grading Proxy", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            
            ColorPageTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabs = tabs,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )

        
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (showNodes) {
                ColorLayerStackPanel(
                    clipId = activeClip.id,
                    layerStack = layerStack,
                    viewModel = layerViewModel,
                    modifier = Modifier.weight(0.35f).fillMaxHeight()
                )
            }
Box(
            modifier = Modifier
                .weight(if (showNodes) 0.65f else 1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Wheels + Basic Light
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        WheelsTabPanel(
                            gradeParams = currentGrade.primaryCorrections,
                            onParamsChange = { newParams ->
                                val newGrade = currentGrade.copy(primaryCorrections = newParams)
                                colorViewModel.updateColorGradeLive(newGrade)
                            },
                            onDragStart = { colorViewModel.beginAdjustmentDrag() },
                            onDragEnd = { colorViewModel.endAdjustmentDrag(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Additional Primary Settings
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1F22), shape = MaterialTheme.shapes.medium)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("PRIMARY SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ColorControlSlider("Temperature", params.temperature, -1f..1f, { colorViewModel.updateAdjustmentParamLive("temperature", it) }, { colorViewModel.updateAdjustmentParamLive("temperature", 0f) })
                                    ColorControlSlider("Tint", params.tint, -1f..1f, { colorViewModel.updateAdjustmentParamLive("tint", it) }, { colorViewModel.updateAdjustmentParamLive("tint", 0f) })
                                    ColorControlSlider("Contrast", params.contrast, -1f..1f, { colorViewModel.updateAdjustmentParamLive("contrast", it) }, { colorViewModel.updateAdjustmentParamLive("contrast", 0f) })
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ColorControlSlider("Saturation", params.saturation, 0f..2f, { colorViewModel.updateAdjustmentParamLive("saturation", it) }, { colorViewModel.updateAdjustmentParamLive("saturation", 1f) })
                                    ColorControlSlider("Vibrance", params.vibrance, -1f..1f, { colorViewModel.updateAdjustmentParamLive("vibrance", it) }, { colorViewModel.updateAdjustmentParamLive("vibrance", 0f) })
                                    ColorControlSlider("Mid Detail", params.clarity, -1f..1f, { colorViewModel.updateAdjustmentParamLive("clarity", it) }, { colorViewModel.updateAdjustmentParamLive("clarity", 0f) })
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Curves
                    CurvesPanel(
                        curveParams = currentGrade.curves,
                        onParamsChange = { newCurves ->
                            val newGrade = currentGrade.copy(curves = newCurves)
                            colorViewModel.updateColorGradeLive(newGrade)
                        },
                        onDragStart = { colorViewModel.beginAdjustmentDrag() },
                        onDragEnd = { colorViewModel.endAdjustmentDrag("Curves") }
                    )
                }
                2 -> {
                    // HSL
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        val isEyedropperActive by colorViewModel.isEyedropperActive.collectAsState()
                        HslPanel(
                            params = currentGrade.hslAdjustments,
                            onParamsChange = { newHsl ->
                                val newGrade = currentGrade.copy(hslAdjustments = newHsl)
                                colorViewModel.updateColorGradeLive(newGrade)
                            },
                            onDragStart = { colorViewModel.beginAdjustmentDrag() },
                            onDragEnd = { colorViewModel.endAdjustmentDrag("HSL") },
                            isEyedropperActive = isEyedropperActive,
                            onToggleEyedropper = { colorViewModel.toggleEyedropper() }
                        )
                        
                        HorizontalDivider(color = Color.DarkGray)
                        
                        SelectiveColorPanel(
                            params = currentGrade.selectiveColor,
                            onParamsChange = { newSelective ->
                                val newGrade = currentGrade.copy(selectiveColor = newSelective)
                                colorViewModel.updateColorGradeLive(newGrade)
                            },
                            onDragStart = { colorViewModel.beginAdjustmentDrag() },
                            onDragEnd = { colorViewModel.endAdjustmentDrag("Selective Color") }
                        )
                        
                        HorizontalDivider(color = Color.DarkGray)
                        
                        SkinToneProtectionPanel(
                            params = params,
                            viewModel = colorViewModel
                        )
                    }
                }
                3 -> {
                    // LUT
                    LutPanel(
                        selectedLutId = params.lutId,
                        intensity = params.lutIntensity,
                        onLutSelected = { id -> colorViewModel.setLutId(id) },
                        onIntensityChanged = { value -> colorViewModel.updateAdjustmentParamLive("lutIntensity", value) }
                    )
                }
                4 -> {
                    // Match
                    MatchPanel(
                        viewModel = colorMatchViewModel,
                        timelineViewModel = timelineViewModel
                    )
                }
                5 -> {
                    val scopeData by scopeViewModel.scopeData.collectAsState()
                    ScopesPanel(
                        scopeData = scopeData,
                        onClose = { /* Not used in tab mode */ },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        } // end Column

        }
        if (showPresets) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha=0.6f)),
                contentAlignment = Alignment.Center
            ) {
                GradePresetPanel(
                    viewModel = colorViewModel,
                    onClose = { showPresets = false }
                )
            }
        }
    } // end outer Box
}
