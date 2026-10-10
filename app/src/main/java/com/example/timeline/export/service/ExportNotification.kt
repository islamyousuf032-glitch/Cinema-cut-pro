package com.example.timeline.export.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.io.File
import androidx.core.content.FileProvider

object ExportNotification {
    private const val CHANNEL_ID = "export_channel"
    const val NOTIFICATION_ID = 1001

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Video Export",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of video exports"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun createProgressNotification(context: Context, percent: Int, stage: String): Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exporting Video")
            .setContentText(stage)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    fun updateProgress(context: Context, percent: Int, stage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createProgressNotification(context, percent, stage))
    }

    fun showCompletedNotification(context: Context, file: File) {
        createChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            viewIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Export Complete")
            .setContentText("Finished exporting: ${file.name}")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    fun showErrorNotification(context: Context, message: String) {
        createChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Export Failed")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }
}
