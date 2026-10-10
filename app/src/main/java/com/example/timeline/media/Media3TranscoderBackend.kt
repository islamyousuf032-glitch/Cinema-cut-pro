package com.example.timeline.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class Media3TranscoderBackend(private val context: Context) : TranscoderBackend {
    override val isAvailable: Boolean = true
    override val name: String = "Media3 Transformer"
    
    private var transformer: Transformer? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun transcode(
        sourceUri: Uri,
        outputUri: Uri,
        settings: ProxySettings
    ): Flow<TranscodeProgress> = callbackFlow {
        trySend(TranscodeProgress.Setup)
        
        val mediaItem = MediaItem.fromUri(sourceUri)
        
        val maxSide = 1280
        val width = settings.sourceWidth
        val height = settings.sourceHeight
        val scaleEffects = mutableListOf<androidx.media3.common.Effect>()
        if (width > 0 && height > 0 && (width > maxSide || height > maxSide)) {
            if (width >= height) {
                var newHeight = (height.toFloat() / width.toFloat() * maxSide).toInt()
                if (newHeight % 2 != 0) newHeight -= 1
                scaleEffects.add(androidx.media3.effect.Presentation.createForWidthAndHeight(maxSide, newHeight, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT))
            } else {
                var newWidth = (width.toFloat() / height.toFloat() * maxSide).toInt()
                if (newWidth % 2 != 0) newWidth -= 1
                scaleEffects.add(androidx.media3.effect.Presentation.createForWidthAndHeight(newWidth, maxSide, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT))
            }
        }
        
        val effects = androidx.media3.common.Effect::class.java
        
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(emptyList(), scaleEffects))
            .build()
        
        val builder = Transformer.Builder(context)
            .setVideoMimeType(settings.videoMimeType)
            
        if (settings.audioMimeType != null) {
            builder.setAudioMimeType(settings.audioMimeType)
        }
        
        transformer = builder.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(TranscodeProgress.Completed)
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    trySend(TranscodeProgress.Error(exportException))
                    close(exportException)
                }
            })
            .build()
            
        transformer?.start(editedMediaItem, outputUri.path ?: "")
        
        var isRunning = true
        val progressHolder = androidx.media3.transformer.ProgressHolder()
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            while (isRunning) {
                val progressState = transformer?.getProgress(progressHolder)
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(TranscodeProgress.Progress(progressHolder.progress / 100f))
                } else if (progressState == Transformer.PROGRESS_STATE_UNAVAILABLE || progressState == Transformer.PROGRESS_STATE_NO_TRANSFORMATION) {
                    // Try waiting or let the listener close it
                }
                kotlinx.coroutines.delay(500)
            }
        }
        
        awaitClose {
            isRunning = false
            transformer?.cancel()
            transformer = null
        }
    }

    override suspend fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}
