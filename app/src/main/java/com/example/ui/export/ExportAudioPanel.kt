package com.example.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TrackType
import com.example.timeline.export.model.ExportSettings

@Composable
fun ExportAudioPanel(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    project: TimelineProject? = null,
    modifier: Modifier = Modifier
) {
    val activeVideoTracks = project?.tracks.orEmpty().filter { track ->
        track.type == TrackType.VIDEO && track.isVisible && track.clips.any { it.isEnabled }
    }
    val activeVideoTrack = activeVideoTracks.singleOrNull()
    val activeClips = activeVideoTrack?.clips?.filter { it.isEnabled }.orEmpty()
    val assetsByClip = activeClips.map { clip ->
        project?.mediaAssets?.firstOrNull { it.assetId == clip.mediaId }
    }
    val embeddedAudioFormats = assetsByClip.mapNotNull { asset ->
        asset?.metadata?.takeIf { it.hasAudio }?.audioStreams?.singleOrNull()
    }.map { it.sampleRate to it.channelCount }.distinct()
    val allClipsHaveAudio = activeClips.isNotEmpty() && assetsByClip.all { it?.metadata?.hasAudio == true }
    val audioExportable = activeVideoTrack?.isMuted == false &&
        allClipsHaveAudio && embeddedAudioFormats.size == 1 &&
        embeddedAudioFormats.singleOrNull()?.let { (rate, channels) -> rate > 0 && channels in 1..2 } == true &&
        project?.tracks.orEmpty().none { track ->
            track.type == TrackType.AUDIO && !track.isMuted && track.clips.any { it.isEnabled }
        }

    val status = when {
        project == null -> "AAC is the only audio codec supported by this export path."
        activeVideoTracks.size != 1 -> "Audio export requires exactly one visible video track."
        activeVideoTrack?.isMuted == true -> "The video track is muted; the export will not include audio."
        activeClips.isEmpty() -> "No active video clips are available for audio export."
        assetsByClip.any { it == null } -> "One or more source clips are missing their media asset."
        project.tracks.any { track ->
            track.type == TrackType.AUDIO && !track.isMuted && track.clips.any { it.isEnabled }
        } -> "Separate audio tracks and audio mixes are not supported by this export path."
        assetsByClip.any { it?.metadata?.hasAudio == true } && !allClipsHaveAudio ->
            "Every clip must have one embedded audio stream, or all clips must be silent. Mixed audio/silent sequences are unsupported."
        allClipsHaveAudio && assetsByClip.any { asset ->
            asset?.metadata?.audioStreams?.size != 1
        } -> "Exactly one embedded audio stream per clip is supported."
        allClipsHaveAudio && embeddedAudioFormats.size > 1 ->
            "Source audio formats differ; sample-rate conversion and audio mixing are not implemented yet."
        allClipsHaveAudio && embeddedAudioFormats.singleOrNull()?.second?.let { it !in 1..2 } == true ->
            "Only mono or stereo embedded audio is supported."
        allClipsHaveAudio -> {
            val (sampleRate, channels) = embeddedAudioFormats.single()
            "AAC ${sampleRate} Hz · ${if (channels == 1) "mono" else "stereo"}; the source sample rate and channel count are preserved."
        }
        else -> "The source clips have no embedded audio; the export will be silent."
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Audio Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("AAC audio", style = MaterialTheme.typography.bodyMedium)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (audioExportable) {
            Column {
                Text("AAC Bitrate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val bitrates = listOf(128_000, 192_000, 256_000, 320_000)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    bitrates.forEach { bitrate ->
                        FilterChip(
                            selected = settings.audioBitrate == bitrate,
                            onClick = { onSettingsChanged(settings.copy(audioBitrate = bitrate)) },
                            label = { Text("${bitrate / 1000} kbps") }
                        )
                    }
                }
            }
        }
    }
}
