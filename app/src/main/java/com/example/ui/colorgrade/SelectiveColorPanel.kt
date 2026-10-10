package com.example.ui.colorgrade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.colorgrade.SelectiveColorParams
import com.example.model.colorgrade.SelectiveColorRange

@Composable
fun SelectiveColorPanel(
    params: SelectiveColorParams,
    onParamsChange: (SelectiveColorParams) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Magenta")
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Selective Color", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        val range = when(selectedTab) {
            0 -> params.red
            1 -> params.orange
            2 -> params.yellow
            3 -> params.green
            4 -> params.cyan
            5 -> params.blue
            6 -> params.purple
            7 -> params.magenta
            else -> params.red
        }

        val updateRange: (SelectiveColorRange) -> Unit = { newRange ->
            val newParams = when(selectedTab) {
                0 -> params.copy(red = newRange)
                1 -> params.copy(orange = newRange)
                2 -> params.copy(yellow = newRange)
                3 -> params.copy(green = newRange)
                4 -> params.copy(cyan = newRange)
                5 -> params.copy(blue = newRange)
                6 -> params.copy(purple = newRange)
                7 -> params.copy(magenta = newRange)
                else -> params
            }
            onParamsChange(newParams)
        }

        ColorControlSlider("Hue Shift", range.hueShift, -0.5f..0.5f, { updateRange(range.copy(hueShift = it)) }, { updateRange(range.copy(hueShift = 0f)) })
        ColorControlSlider("Saturation", range.saturation, -1f..1f, { updateRange(range.copy(saturation = it)) }, { updateRange(range.copy(saturation = 0f)) })
        ColorControlSlider("Luminance", range.luminance, -1f..1f, { updateRange(range.copy(luminance = it)) }, { updateRange(range.copy(luminance = 0f)) })
        ColorControlSlider("Softness", range.softness, 0.01f..0.5f, { updateRange(range.copy(softness = it)) }, { updateRange(range.copy(softness = 0.1f)) })
    }
}
