package com.example.ui.colorgrade

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.colorgrade.ColorGradeParams
import com.example.model.colorgrade.ColorWheelParams

@Composable
fun LogWheelsPanel(
    gradeParams: ColorGradeParams,
    onParamsChange: (ColorGradeParams) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ColorWheelControl(
            title = "Shadows",
            params = ColorWheelParams(gradeParams.shadows.r, gradeParams.shadows.g, gradeParams.shadows.b, gradeParams.shadows.luma),
            modifier = Modifier.width(220.dp),
            onVectorChange = { r, g, b ->
                val newWheel = gradeParams.shadows.copy(r = r, g = g, b = b)
                onParamsChange(gradeParams.copy(shadows = newWheel))
            },
            onLumaChange = { l ->
                val newWheel = gradeParams.shadows.copy(luma = l)
                onParamsChange(gradeParams.copy(shadows = newWheel))
            },
            onReset = {
                onParamsChange(gradeParams.copy(shadows = gradeParams.shadows.copy(r=0f, g=0f, b=0f, luma=0f)))
            },
            onDragStart = onDragStart,
            onDragEnd = onDragEnd
        )
        ColorWheelControl(
            title = "Midtones",
            params = ColorWheelParams(gradeParams.midtones.r, gradeParams.midtones.g, gradeParams.midtones.b, gradeParams.midtones.luma),
            modifier = Modifier.width(220.dp),
            onVectorChange = { r, g, b ->
                val newWheel = gradeParams.midtones.copy(r = r, g = g, b = b)
                onParamsChange(gradeParams.copy(midtones = newWheel))
            },
            onLumaChange = { l ->
                val newWheel = gradeParams.midtones.copy(luma = l)
                onParamsChange(gradeParams.copy(midtones = newWheel))
            },
            onReset = {
                onParamsChange(gradeParams.copy(midtones = gradeParams.midtones.copy(r=0f, g=0f, b=0f, luma=0f)))
            },
            onDragStart = onDragStart,
            onDragEnd = onDragEnd
        )
        ColorWheelControl(
            title = "Highlights",
            params = ColorWheelParams(gradeParams.highlights.r, gradeParams.highlights.g, gradeParams.highlights.b, gradeParams.highlights.luma),
            modifier = Modifier.width(220.dp),
            onVectorChange = { r, g, b ->
                val newWheel = gradeParams.highlights.copy(r = r, g = g, b = b)
                onParamsChange(gradeParams.copy(highlights = newWheel))
            },
            onLumaChange = { l ->
                val newWheel = gradeParams.highlights.copy(luma = l)
                onParamsChange(gradeParams.copy(highlights = newWheel))
            },
            onReset = {
                onParamsChange(gradeParams.copy(highlights = gradeParams.highlights.copy(r=0f, g=0f, b=0f, luma=0f)))
            },
            onDragStart = onDragStart,
            onDragEnd = onDragEnd
        )
        // Offset could be included in log wheels if needed, but DaVinci usually has Offset everywhere
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
}
