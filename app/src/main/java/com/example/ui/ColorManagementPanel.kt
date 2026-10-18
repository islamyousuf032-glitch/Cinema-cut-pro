package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.example.model.adjustments.LogProfile
import com.example.model.adjustments.ColorSpaceProfile
import com.example.model.adjustments.engine.ToneMappingMode
import com.example.timeline.ui.TimelineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorManagementPanel(timelineViewModel: TimelineViewModel) {
    val cmViewModel: ColorManagementViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ColorManagementViewModel(timelineViewModel) as T
            }
        }
    )

    val uiState by timelineViewModel.uiState.collectAsState()
    val activeClip = timelineViewModel.getActiveVideoClipAndAsset()?.first

    if (activeClip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a clip to manage color", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val currentProfile = cmViewModel.getSelectedClipProfile()
    val params = activeClip.adjustments.params
    var expandedProfile by remember { mutableStateOf(false) }
    var expandedWorking by remember { mutableStateOf(false) }
    var expandedOutput by remember { mutableStateOf(false) }
    var expandedToneMap by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COLOR MANAGEMENT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            if (currentProfile != LogProfile.NONE && currentProfile != LogProfile.REC_709) {
                Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("LOG", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else if (params.inputColorSpace == ColorSpaceProfile.REC_2020 || params.inputColorSpace == ColorSpaceProfile.REC_2020_LINEAR) {
                Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("REC.2020", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else if (params.outputColorSpace == ColorSpaceProfile.HLG || params.outputColorSpace == ColorSpaceProfile.PQ) {
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("HDR", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else {
                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("SDR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Input Settings (Per Clip)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Camera Log Profile", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = expandedProfile,
                    onExpandedChange = { expandedProfile = !expandedProfile }
                ) {
                    TextField(
                        value = currentProfile.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedProfile,
                        onDismissRequest = { expandedProfile = false }
                    ) {
                        LogProfile.values().forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.displayName) },
                                onClick = {
                                    cmViewModel.assignInputProfile(profile)
                                    expandedProfile = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Working Color Space", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = expandedWorking,
                    onExpandedChange = { expandedWorking = !expandedWorking }
                ) {
                    TextField(
                        value = params.inputColorSpace.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWorking) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedWorking,
                        onDismissRequest = { expandedWorking = false }
                    ) {
                        ColorSpaceProfile.values().forEach { space ->
                            DropdownMenuItem(
                                text = { Text(space.name) },
                                onClick = {
                                    cmViewModel.updateParam("inputColorSpace", space)
                                    expandedWorking = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Non-destructive conversion applied before adjustments.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (currentProfile != LogProfile.NONE) {
                    Text("Gamut Warning: Profile may output values outside valid range.", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tone Mapping / Output", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Output Target", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = expandedOutput,
                    onExpandedChange = { expandedOutput = !expandedOutput }
                ) {
                    TextField(
                        value = params.outputColorSpace.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOutput) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedOutput,
                        onDismissRequest = { expandedOutput = false }
                    ) {
                        ColorSpaceProfile.values().forEach { space ->
                            DropdownMenuItem(
                                text = { Text(space.name) },
                                onClick = {
                                    cmViewModel.updateParam("outputColorSpace", space)
                                    expandedOutput = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Tone Mapping Mode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = expandedToneMap,
                    onExpandedChange = { expandedToneMap = !expandedToneMap }
                ) {
                    TextField(
                        value = params.toneMappingMode.name, // Will use name
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedToneMap) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedToneMap,
                        onDismissRequest = { expandedToneMap = false }
                    ) {
                        ToneMappingMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    cmViewModel.updateParam("toneMappingMode", mode)
                                    expandedToneMap = false
                                }
                            )
                        }
                    }
                }

                if (params.toneMappingMode == ToneMappingMode.HIGHLIGHT_ROLLOFF) {
                    Spacer(Modifier.height(8.dp))
                    Text("Highlight Rolloff", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = params.highlightRolloff,
                        onValueChange = { cmViewModel.updateParamFloat("highlightRolloff", it) },
                        valueRange = 0f..1f
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("10-bit pipeline active where supported.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                if (params.outputColorSpace == ColorSpaceProfile.HLG || params.outputColorSpace == ColorSpaceProfile.PQ) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("HDR Warning: HDR display may not be fully supported by emulator or uncalibrated screens.", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
