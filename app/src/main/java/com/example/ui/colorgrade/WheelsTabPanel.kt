package com.example.ui.colorgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.colorgrade.ColorGradeParams

@Composable
fun WheelsTabPanel(
    gradeParams: ColorGradeParams,
    onParamsChange: (ColorGradeParams) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf("Primary Wheels") }
    val subTabs = listOf("Primary Wheels", "Log Wheels")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sub-tabs for Wheels
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C30))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            subTabs.forEach { tab ->
                val isSelected = selectedSubTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { selectedSubTab = tab }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color.Gray
                    )
                }
            }
        }

        // Active Wheels Panel
        when (selectedSubTab) {
            "Primary Wheels" -> {
                PrimaryWheelsPanel(
                    gradeParams = gradeParams,
                    onParamsChange = onParamsChange,
                    onDragStart = onDragStart,
                    onDragEnd = { onDragEnd("Primary Wheels") }
                )
            }
            "Log Wheels" -> {
                LogWheelsPanel(
                    gradeParams = gradeParams,
                    onParamsChange = onParamsChange,
                    onDragStart = onDragStart,
                    onDragEnd = { onDragEnd("Log Wheels") }
                )
            }
        }
    }
}
