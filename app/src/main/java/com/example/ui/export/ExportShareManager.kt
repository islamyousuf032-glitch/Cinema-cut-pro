package com.example.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ExportShareManager {
    fun shareVideo(context: Context, videoFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playVideo(context: Context, videoFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
