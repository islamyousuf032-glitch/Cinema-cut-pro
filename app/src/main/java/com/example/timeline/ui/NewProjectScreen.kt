package com.example.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timeline.core.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewProjectScreen(
    onCreateProject: (ProjectSettings) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ProjectCreationViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Create New Project", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.projectName,
                onValueChange = { viewModel.setProjectName(it) },
                label = { Text("Project Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Aspect Ratio
            Text("Aspect Ratio", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectAspectRatioPreset.values().forEach { ar ->
                    FilterChip(
                        selected = uiState.aspectRatio == ar,
                        onClick = { viewModel.setAspectRatio(ar) },
                        label = { Text(ar.displayName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Resolution
            Text("Resolution", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.resolutionOptions.forEach { res ->
                    FilterChip(
                        selected = uiState.resolutionWidth == res.width && uiState.resolutionHeight == res.height,
                        onClick = { viewModel.setResolution(res.width, res.height) },
                        label = { Text(res.display) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Frame Rate
            Text("Frame Rate", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectFrameRatePreset.values().forEach { fr ->
                    FilterChip(
                        selected = uiState.frameRatePreset == fr,
                        onClick = { viewModel.setFrameRate(fr) },
                        label = { Text(fr.displayName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Color Space
            Text("Color Space", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectColorSpace.values().forEach { cs ->
                    FilterChip(
                        selected = uiState.colorSpace == cs,
                        onClick = { viewModel.setColorSpace(cs) },
                        label = { Text(cs.displayName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Audio Sample Rate
            Text("Audio Sample Rate", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectAudioSampleRate.values().forEach { sr ->
                    FilterChip(
                        selected = uiState.audioSampleRate == sr,
                        onClick = { viewModel.setAudioSampleRate(sr) },
                        label = { Text(sr.displayName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val settings = ProjectSettings(
                            projectId = java.util.UUID.randomUUID().toString(),
                            projectName = uiState.projectName,
                            aspectRatioPreset = uiState.aspectRatio,
                            resolutionWidth = uiState.resolutionWidth,
                            resolutionHeight = uiState.resolutionHeight,
                            frameRate = uiState.frameRatePreset,
                            colorSpace = uiState.colorSpace,
                            audioSampleRate = uiState.audioSampleRate
                        )
                        onCreateProject(settings)
                    },
                    enabled = uiState.isValid,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("Create Project")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
