package com.example.timeline.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class Media3TranscoderBackend(private val context: Context) : TranscoderBackend {
    override val isAvailable: Boolean = true
    override val name: String = "Media3 Transformer"
    
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var transformer: Transformer? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun transcode(
        sourceUri: Uri,
        outputUri: Uri,
        settings: ProxySettings
    ): Flow<TranscodeProgress> = callbackFlow {
        trySend(TranscodeProgress.Setup)
        val outputFile = File(outputUri.path ?: error("Proxy output must be a file URI."))
        check(outputFile.parentFile?.isDirectory == true) { "Proxy output directory is unavailable." }
        check(!outputFile.exists() || outputFile.delete()) { "Previous proxy output could not be replaced." }
        val completedSuccessfully = AtomicBoolean(false)
        val isRunning = AtomicBoolean(true)
        var progressJob: Job? = null

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
        
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(emptyList(), scaleEffects))
            .build()
        
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (!outputFile.isFile || outputFile.length() <= 0L) {
                    val failure = IllegalStateException(
                        "Media3 Transformer reported completion without a non-empty proxy file."
                    )
                    trySend(TranscodeProgress.Error(failure))
                    close(failure)
                    return
                }
                completedSuccessfully.set(true)
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
        }

        val activeTransformer = try {
            withContext(Dispatchers.Main.immediate) {
                val builder = Transformer.Builder(context)
                    .setVideoMimeType(settings.videoMimeType)
                if (settings.audioMimeType != null) builder.setAudioMimeType(settings.audioMimeType)
                builder.addListener(listener).build().also { builtTransformer ->
                    transformer = builtTransformer
                    builtTransformer.start(editedMediaItem, outputFile.absolutePath)
                }
            }
        } catch (error: Exception) {
            val failedTransformer = transformer
            transformer = null
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                runCatching { failedTransformer?.cancel() }
            }
            outputFile.delete()
            throw error
        }

        val progressHolder = androidx.media3.transformer.ProgressHolder()

        progressJob = launch(Dispatchers.IO) {
            while (isRunning.get()) {
                val progressState = withContext(Dispatchers.Main.immediate) {
                    activeTransformer.getProgress(progressHolder)
                }
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(TranscodeProgress.Progress(progressHolder.progress / 100f))
                }
                // Completion and errors are reported by the Transformer listener; other progress
                // states are transient or permanently unavailable and need no separate action.
                delay(500)
            }
        }

        awaitClose {
            isRunning.set(false)
            progressJob?.cancel()
            val active = transformer
            transformer = null
            if (!completedSuccessfully.get()) {
                val cancelAndDelete = Runnable {
                    runCatching { active?.cancel() }
                    outputFile.delete()
                }
                if (Looper.myLooper() == Looper.getMainLooper()) cancelAndDelete.run()
                else mainHandler.post(cancelAndDelete)
            }
        }
    }

    override suspend fun cancel() {
        val active = transformer
        transformer = null
        withContext(Dispatchers.Main.immediate) {
            active?.cancel()
        }
    }
}
