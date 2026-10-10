package com.example.timeline.media

import android.content.Context
import android.content.Intent
import android.net.Uri

object ImportPermissionManager {
    fun persistPermission(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
