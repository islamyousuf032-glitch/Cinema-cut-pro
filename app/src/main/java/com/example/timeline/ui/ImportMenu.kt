package com.example.timeline.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun ImportMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImportVideo: () -> Unit,
    onImportAudio: () -> Unit,
    onImportImage: () -> Unit,
    onImportImageSequence: () -> Unit,
    onImportAny: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(text = { Text("Import Video") }, onClick = { onDismissRequest(); onImportVideo() })
        DropdownMenuItem(text = { Text("Import Audio") }, onClick = { onDismissRequest(); onImportAudio() })
        DropdownMenuItem(text = { Text("Import Images") }, onClick = { onDismissRequest(); onImportImage() })
        DropdownMenuItem(text = { Text("Import Image Sequence") }, onClick = { onDismissRequest(); onImportImageSequence() })
        DropdownMenuItem(text = { Text("Import Any File") }, onClick = { onDismissRequest(); onImportAny() })
    }
}
