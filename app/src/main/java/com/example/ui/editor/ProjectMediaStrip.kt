package com.example.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.timeline.media.MediaAsset
import com.example.timeline.ui.TimelineViewModel

@Composable
fun ProjectMediaStrip(
    mediaAssets: List<MediaAsset>,
    proxyProgressMap: Map<String, Float>,
    launchers: com.example.timeline.ui.MediaImportLaunchers,
    timelineViewModel: TimelineViewModel,
    onRelinkRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    com.example.timeline.ui.MediaBinPanel(
        mediaAssets = mediaAssets,
        proxyProgressMap = proxyProgressMap,
        onImportVideoClick = { launchers.videoLauncher.launch(arrayOf("video/*")) },
        onImportAudioClick = { launchers.audioLauncher.launch(arrayOf("audio/*")) },
        onImportImageClick = { launchers.imageLauncher.launch(arrayOf("image/*")) },
        onImportImageSequenceClick = { launchers.imageSequenceLauncher.launch(arrayOf("image/*")) },
        onImportAnyClick = { launchers.anyLauncher.launch(arrayOf("*/*")) },
        onAssetClick = { asset -> timelineViewModel.addClipFromMedia(asset) },
        onAssetOptionClick = { asset, mode -> timelineViewModel.addClipFromMediaWithMode(asset, mode) },
        onRetryImport = { timelineViewModel.retryMediaAsset(it) },
        onCancelProxy = { timelineViewModel.cancelProxy(it) },
        onRelinkRequest = onRelinkRequest,
        modifier = modifier
    )
}
