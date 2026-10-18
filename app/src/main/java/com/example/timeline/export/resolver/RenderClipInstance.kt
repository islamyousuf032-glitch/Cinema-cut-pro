package com.example.timeline.export.resolver

import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.core.TimelineClip
import com.example.timeline.media.MediaAsset

data class RenderClipInstance(
    val clip: TimelineClip,
    val mediaAsset: MediaAsset?,
    val timelineTrackId: String,
    val zIndex: Int, 
    val evaluateTransform: ClipTransform,  
    val sourceTimeUs: Long,
    val sourceFrame: Long,
    val isAudioEnabled: Boolean,
    val isVideoEnabled: Boolean,
    val useProxyMode: Boolean
)
