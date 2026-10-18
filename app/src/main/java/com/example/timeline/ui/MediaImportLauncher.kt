package com.example.timeline.ui

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.example.timeline.media.MediaPickerContract

data class MediaImportLaunchers(
    val videoLauncher: ManagedActivityResultLauncher<Array<String>, List<@JvmSuppressWildcards Uri>>,
    val audioLauncher: ManagedActivityResultLauncher<Array<String>, List<@JvmSuppressWildcards Uri>>,
    val imageLauncher: ManagedActivityResultLauncher<Array<String>, List<@JvmSuppressWildcards Uri>>,
    val imageSequenceLauncher: ManagedActivityResultLauncher<Array<String>, List<@JvmSuppressWildcards Uri>>,
    val anyLauncher: ManagedActivityResultLauncher<Array<String>, List<@JvmSuppressWildcards Uri>>
)

@Composable
fun rememberMediaImportLaunchers(onUrisPicked: (List<Uri>) -> Unit): MediaImportLaunchers {
    val video = rememberLauncherForActivityResult(MediaPickerContract.OpenMultiple) { onUrisPicked(it) }
    val audio = rememberLauncherForActivityResult(MediaPickerContract.OpenMultiple) { onUrisPicked(it) }
    val image = rememberLauncherForActivityResult(MediaPickerContract.OpenMultiple) { onUrisPicked(it) }
    val imageSequence = rememberLauncherForActivityResult(MediaPickerContract.OpenMultiple) { onUrisPicked(it) }
    val any = rememberLauncherForActivityResult(MediaPickerContract.OpenMultiple) { onUrisPicked(it) }
    return MediaImportLaunchers(video, audio, image, imageSequence, any)
}
