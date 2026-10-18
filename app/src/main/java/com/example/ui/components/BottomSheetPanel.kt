package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EditorIcons
import com.example.ui.theme.EditorSpacing

@Composable
fun BottomSheetPanel(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = EditorSpacing.Small)
    ) {
        // Drag handle pill
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                .align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(EditorSpacing.Medium))
        
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EditorSpacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            if (onClose != null) {
                IconToolButton(
                    icon = EditorIcons.Close,
                    onClick = onClose,
                    modifier = Modifier.size(EditorSpacing.MinTouchTarget)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(EditorSpacing.Medium))
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EditorSpacing.Large, vertical = EditorSpacing.Small),
            content = content
        )
    }
}
