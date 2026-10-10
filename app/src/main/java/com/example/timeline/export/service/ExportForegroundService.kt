package com.example.timeline.export.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import com.example.timeline.export.BatchExportManager

class ExportForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_START_EXPORT") {
            if (job?.isActive == true) return START_NOT_STICKY
            val notification = ExportNotification.createProgressNotification(this, 0, "Starting export...")
            startForeground(ExportNotification.NOTIFICATION_ID, notification)
            
            job = scope.launch {
                try {
                    do {
                        BatchExportManager.runExportInternal(this@ExportForegroundService)
                    } while (BatchExportManager.promoteNextQueuedExport())
                } catch (cancelled: CancellationException) {
                    Log.i("ExportService", "Export worker cancelled")
                } catch (e: Exception) {
                    Log.e("ExportService", "Export failed", e)
                } finally {
                    stopForeground(true)
                    stopSelf()
                    val workerJob = currentCoroutineContext()[Job]
                    val appContext = applicationContext
                    scope.launch {
                        workerJob?.join()
                        BatchExportManager.onExportServiceStopped(appContext)
                    }
                }
            }
        } else if (intent?.action == "ACTION_CANCEL_EXPORT") {
            ExportCancelController.cancel()
            job?.cancel()
            stopForeground(true)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
