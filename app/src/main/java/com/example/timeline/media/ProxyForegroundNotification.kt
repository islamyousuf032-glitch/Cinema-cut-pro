package com.example.timeline.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

/** User-visible foreground notification required while a proxy is being transcoded. */
internal object ProxyForegroundNotification {
    private const val CHANNEL_ID = "proxy_generation"
    private const val CHANNEL_NAME = "Proxy generation"

    fun create(context: Context, workId: UUID, progressPercent: Int?): ForegroundInfo {
        createChannel(context)
        val percent = progressPercent?.coerceIn(0, 100) ?: 0
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Generating media proxy")
            .setContentText(
                if (progressPercent == null) "Preparing media…" else "Transcoding media… $percent%"
            )
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(100, percent, progressPercent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                WorkManager.getInstance(context).createCancelPendingIntent(workId)
            )
            .build()

        val notificationId = workId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress while a media proxy is generated"
                }
            )
        }
    }
}
