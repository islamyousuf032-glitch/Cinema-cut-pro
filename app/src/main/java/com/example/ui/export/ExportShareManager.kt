package com.example.ui.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.example.timeline.export.service.ExportedVideo

object ExportShareManager {
    fun shareVideo(context: Context, video: ExportedVideo) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType
            putExtra(Intent.EXTRA_STREAM, video.uri)
            clipData = ClipData.newUri(context.contentResolver, video.displayName, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
        }
    }

    fun openVideo(context: Context, video: ExportedVideo) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(video.uri, video.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(intent)
        }
    }
}
