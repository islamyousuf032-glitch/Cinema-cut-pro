package com.example.timeline.media

import android.net.Uri

interface MediaAnalyzer {
    suspend fun analyze(asset: MediaAsset, uri: Uri): MediaAsset
}
