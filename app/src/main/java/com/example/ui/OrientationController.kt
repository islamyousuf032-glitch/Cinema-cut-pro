package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo

object OrientationController {
    fun requestLandscape(context: Context): Int? {
        val activity = context as? Activity ?: return null
        val original = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        return original
    }

    fun restoreOrientation(context: Context, originalOrientation: Int?) {
        if (originalOrientation == null) return
        val activity = context as? Activity ?: return
        activity.requestedOrientation = originalOrientation
    }
}
