package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlcSmokeTestScreen(
    videoPath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(listOf<String>()) }
    var isPlaying by remember { mutableStateOf(false) }

    fun addLog(msg: String) {
        logs = logs + msg
        Log.d("VLC_SMOKE", msg)
    }

    var libVlc by remember { mutableStateOf<LibVLC?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(videoPath) {
        addLog("libvlc init start")
        val vlc = try {
            val options = arrayListOf("-vvv")
            LibVLC(context, options).also {
                addLog("libvlc init success")
            }
        } catch (e: Exception) {
            addLog("libvlc init fail: ${e.message}")
            null
        }

        val player = vlc?.let { MediaPlayer(it) }?.apply {
            setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Opening -> addLog("event Opening")
                    MediaPlayer.Event.Buffering -> addLog("event Buffering ${event.buffering}%")
                    MediaPlayer.Event.Playing -> {
                        addLog("event Playing")
                        isPlaying = true
                    }
                    MediaPlayer.Event.Paused -> isPlaying = false
                    MediaPlayer.Event.Stopped -> isPlaying = false
                    MediaPlayer.Event.EndReached -> addLog("event EndReached")
                    MediaPlayer.Event.EncounteredError -> addLog("event EncounteredError")
                    MediaPlayer.Event.TimeChanged -> {
                        // Too noisy to log every time
                        // addLog("event TimeChanged = ${event.timeChanged}")
                    }
                    MediaPlayer.Event.Vout -> addLog("event Vout count = ${event.voutCount}")
                }
            }
        }

        if (player != null) {
            addLog("mediaPlayer created")
        }

        libVlc = vlc
        mediaPlayer = player

        onDispose {
            player?.stop()
            player?.detachViews()
            player?.release()
            vlc?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VLC Smoke Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                if (mediaPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            addLog("videoLayout created")
                            VLCVideoLayout(ctx).apply {
                                if (mediaPlayer?.vlcVout?.areViewsAttached() == true) {
                                    mediaPlayer?.vlcVout?.detachViews()
                                }
                                mediaPlayer?.attachViews(this, null, false, false)
                                addLog("attaching views")
                            }
                        },
                        onRelease = { view ->
                            try {
                                if (mediaPlayer?.vlcVout?.areViewsAttached() == true) {
                                    mediaPlayer?.vlcVout?.detachViews()
                                }
                            } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Row {
                    Button(onClick = {
                        val parsedUri = Uri.parse(videoPath)
                        val path = if (parsedUri.scheme == "file") parsedUri.path ?: videoPath else videoPath
                        val file = File(path)
                        addLog("local path = $path")
                        addLog("file exists = ${file.exists()}")
                        addLog("file size = ${file.length()}")
                        
                        if (file.exists() && file.length() > 0) {
                            val media = Media(libVlc, Uri.fromFile(file))
                            mediaPlayer?.media = media
                            media.release()
                            addLog("media set")
                            mediaPlayer?.play()
                            addLog("play called")
                        } else {
                            addLog("File missing or empty")
                        }
                    }) {
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { mediaPlayer?.pause() }) {
                        Text("Pause")
                    }
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs.reversed()) { logMsg ->
                        Text(logMsg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
