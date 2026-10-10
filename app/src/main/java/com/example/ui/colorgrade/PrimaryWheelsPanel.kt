package com.example.ui.colorgrade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.colorgrade.ColorGradeStack
import com.example.model.colorgrade.ColorGradeParams
import com.example.model.colorgrade.ColorWheelParams
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme

@Composable
fun PrimaryWheelsPanel(
    gradeParams: ColorGradeParams,
    onParamsChange: (ColorGradeParams) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var basicExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var tonalExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ColorWheelControl(
                title = "Lift",
                params = gradeParams.lift,
                modifier = Modifier.width(220.dp),
                onVectorChange = { r, g, b ->
                    val newWheel = gradeParams.lift.copy(r = r, g = g, b = b)
                    onParamsChange(gradeParams.copy(lift = newWheel))
                },
                onLumaChange = { l ->
                    val newWheel = gradeParams.lift.copy(luma = l)
                    onParamsChange(gradeParams.copy(lift = newWheel))
                },
                onReset = {
                    onParamsChange(gradeParams.copy(lift = ColorWheelParams()))
                },
                onDragStart = onDragStart,
                onDragEnd = onDragEnd
            )

            ColorWheelControl(
                title = "Gamma",
                params = gradeParams.gamma,
                modifier = Modifier.width(220.dp),
                onVectorChange = { r, g, b ->
                    val newWheel = gradeParams.gamma.copy(r = r, g = g, b = b)
                    onParamsChange(gradeParams.copy(gamma = newWheel))
                },
                onLumaChange = { l ->
                    val newWheel = gradeParams.gamma.copy(luma = l)
                    onParamsChange(gradeParams.copy(gamma = newWheel))
                },
                onReset = {
                    onParamsChange(gradeParams.copy(gamma = ColorWheelParams()))
                },
                onDragStart = onDragStart,
                onDragEnd = onDragEnd
            )

            ColorWheelControl(
                title = "Gain",
                params = gradeParams.gain,
                modifier = Modifier.width(220.dp),
                onVectorChange = { r, g, b ->
                    val newWheel = gradeParams.gain.copy(r = r, g = g, b = b)
                    onParamsChange(gradeParams.copy(gain = newWheel))
                },
                onLumaChange = { l ->
                    val newWheel = gradeParams.gain.copy(luma = l)
                    onParamsChange(gradeParams.copy(gain = newWheel))
                },
                onReset = {
                    onParamsChange(gradeParams.copy(gain = ColorWheelParams()))
                },
                onDragStart = onDragStart,
                onDragEnd = onDragEnd
            )

            ColorWheelControl(
                title = "Offset",
                params = gradeParams.offset,
                modifier = Modifier.width(220.dp),
                onVectorChange = { r, g, b ->
                    val newWheel = gradeParams.offset.copy(r = r, g = g, b = b)
                    onParamsChange(gradeParams.copy(offset = newWheel))
                },
                onLumaChange = { l ->
                    val newWheel = gradeParams.offset.copy(luma = l)
                    onParamsChange(gradeParams.copy(offset = newWheel))
                },
                onReset = {
                    onParamsChange(gradeParams.copy(offset = ColorWheelParams()))
                },
                onDragStart = onDragStart,
                onDragEnd = onDragEnd
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Debug Diagnostics
        val renderer = androidx.compose.runtime.remember { com.example.ui.colorgrade.ColorWheelRenderer() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1F22), shape = MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            Text("DEVICE RENDER DIAGNOSTICS", color = Color.White, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Model: ${com.example.ui.colorgrade.DeviceRenderDiagnostics.deviceModel} (${com.example.ui.colorgrade.DeviceRenderDiagnostics.deviceManufacturer})", color = Color.LightGray, fontSize = 11.sp)
            Text("Android: ${com.example.ui.colorgrade.DeviceRenderDiagnostics.androidVersion} (API ${com.example.ui.colorgrade.DeviceRenderDiagnostics.sdkInt})", color = Color.LightGray, fontSize = 11.sp)
            Text("Render Mode: ${renderer.activeMode.name}", color = Color.Green, fontSize = 11.sp)
            if (renderer.activeMode != com.example.ui.colorgrade.ColorWheelRenderMode.COMPOSE_SWEEP_GRADIENT) {
                Text("Fallback Reason: ${renderer.fallbackReason}", color = Color.Yellow, fontSize = 11.sp)
            }
        }
    }
}
