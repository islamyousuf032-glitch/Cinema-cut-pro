package com.example.ui.colorgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ColorAdjustmentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradePresetPanel(
    viewModel: ColorAdjustmentViewModel,
    onClose: () -> Unit
) {
    var newPresetName by remember { mutableStateOf("") }

    // Fake list of presets since we only stubbed the viewmodel for now, 
    // but in a real app this would collect from the ViewModel.
    val presets = remember { mutableStateListOf("Cinematic Teal", "Warm Summer", "Moody Dark") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GRADE PRESETS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                TextButton(onClick = onClose) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    placeholder = { Text("Preset name...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151515),
                        unfocusedContainerColor = Color(0xFF151515),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            viewModel.saveAdjustmentPreset(newPresetName)
                            presets.add(newPresetName)
                            newPresetName = ""
                        }
                    },
                    modifier = Modifier.height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { presetName ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Color(0xFF2A2D31), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.applyAdjustmentPreset(presetName)
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(presetName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
