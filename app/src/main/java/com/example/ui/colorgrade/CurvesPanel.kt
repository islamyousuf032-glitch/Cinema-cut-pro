package com.example.ui.colorgrade

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.colorgrade.ColorGradeParams
import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.CurvePoint

@Composable
fun CurvesPanel(
    curveParams: CurveParams,
    onParamsChange: (CurveParams) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Master", "Red", "Green", "Blue", "Luma", "Hue vs Hue", "Hue vs Sat", "Hue vs Luma", "Sat vs Sat", "Luma vs Sat")
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

        val lineColor = when(selectedTab) {
            1 -> Color.Red
            2 -> Color.Green
            3 -> Color.Blue
            else -> Color.White
        }

        val currentPoints = when(selectedTab) {
            0 -> curveParams.masterPoints
            1 -> curveParams.redPoints
            2 -> curveParams.greenPoints
            3 -> curveParams.bluePoints
            4 -> curveParams.lumaPoints
            5 -> curveParams.hueVsHuePoints
            6 -> curveParams.hueVsSatPoints
            7 -> curveParams.hueVsLumaPoints
            8 -> curveParams.satVsSatPoints
            9 -> curveParams.lumaVsSatPoints
            else -> curveParams.masterPoints
        }

        CurveEditorControl(
            points = currentPoints,
            onPointsChanged = { newPoints ->
                val newParams = when(selectedTab) {
                    0 -> curveParams.copy(masterPoints = newPoints)
                    1 -> curveParams.copy(redPoints = newPoints)
                    2 -> curveParams.copy(greenPoints = newPoints)
                    3 -> curveParams.copy(bluePoints = newPoints)
                    4 -> curveParams.copy(lumaPoints = newPoints)
                    5 -> curveParams.copy(hueVsHuePoints = newPoints)
                    6 -> curveParams.copy(hueVsSatPoints = newPoints)
                    7 -> curveParams.copy(hueVsLumaPoints = newPoints)
                    8 -> curveParams.copy(satVsSatPoints = newPoints)
                    9 -> curveParams.copy(lumaVsSatPoints = newPoints)
                    else -> curveParams
                }
                onParamsChange(newParams)
            },
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            lineColor = lineColor
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    val defaultPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
                    val defaultHuePoints = listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f))
                    
                    val newParams = when(selectedTab) {
                        0 -> curveParams.copy(masterPoints = defaultPoints)
                        1 -> curveParams.copy(redPoints = defaultPoints)
                        2 -> curveParams.copy(greenPoints = defaultPoints)
                        3 -> curveParams.copy(bluePoints = defaultPoints)
                        4 -> curveParams.copy(lumaPoints = defaultPoints)
                        5 -> curveParams.copy(hueVsHuePoints = defaultHuePoints)
                        6 -> curveParams.copy(hueVsSatPoints = defaultHuePoints)
                        7 -> curveParams.copy(hueVsLumaPoints = defaultHuePoints)
                        8 -> curveParams.copy(satVsSatPoints = defaultHuePoints)
                        9 -> curveParams.copy(lumaVsSatPoints = defaultHuePoints)
                        else -> curveParams
                    }
                    onDragStart()
                    onParamsChange(newParams)
                    onDragEnd()
                }
            ) {
                Text("Reset Curve", color = Color.LightGray)
            }
        }
    }
}
