package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromaProApp(viewModel: MainViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val timelineViewModel: com.example.timeline.ui.TimelineViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.timeline.ui.TimelineViewModel(context) as T
            }
        }
    )
    val timelineUiState by timelineViewModel.uiState.collectAsState()
    val playheadFrame by timelineViewModel.playheadFrame.collectAsState()
    val isPlaying by timelineViewModel.isPlaying.collectAsState()
    val colorAdjustmentViewModel: com.example.ui.ColorAdjustmentViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.ColorAdjustmentViewModel(timelineViewModel) as T
            }
        }
    )
    val colorMatchViewModel: com.example.ui.ColorMatchViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.ColorMatchViewModel(timelineViewModel, context) as T
            }
        }
    )
    val isSampling by colorAdjustmentViewModel.isEyedropperActive.collectAsState()

    val scopeViewModel: com.example.ui.scopes.ScopeViewModel = viewModel()
    val scopeData by scopeViewModel.scopeData.collectAsState()
    val isAnalyzing by scopeViewModel.isAnalyzing.collectAsState()
    val editorViewModel: com.example.ui.editor.EditorViewModel = viewModel()
    
    val timelinePreviewController = remember { com.example.timeline.engine.preview.TimelinePreviewController(context) }
    
    androidx.compose.runtime.DisposableEffect(timelinePreviewController, timelineViewModel, editorViewModel) {
        timelinePreviewController.onPlayheadAdvanced = { newFrame ->
            editorViewModel.updatePlayheadFrame(newFrame)
            timelineViewModel.setPlayheadFrame(newFrame)
        }
        onDispose { timelinePreviewController.onPlayheadAdvanced = null }
    }
    

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (com.example.timeline.core.ActiveProjectManager.isDirty) {
                    com.example.timeline.core.ActiveProjectManager.activeProject?.let { 
                        timelineViewModel.updateStateWithoutHistory(it) 
                    }
                    editorViewModel.updatePlayheadFrame(com.example.timeline.core.ActiveProjectManager.playheadFrame)
                    timelineViewModel.setPlayheadFrame(com.example.timeline.core.ActiveProjectManager.playheadFrame)
                    timelineViewModel.selectClip(com.example.timeline.core.ActiveProjectManager.selectedClipId)
                    com.example.timeline.core.ActiveProjectManager.isDirty = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showScopes by remember { mutableStateOf(false) }
    var showAdvancedTimeline by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var smokeTestVideoPath by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Color", "Transform", "Motion", "Graph Editor", "Color Space", "Timeline")

    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playheadFrame, isPlaying, showScopes, timelineUiState.project) {
        if (showScopes && timelineUiState.selectedClipId != null) {
            val clipId = timelineUiState.selectedClipId
            val track = timelineUiState.project.tracks.firstOrNull { it.clips.any { c -> c.id == clipId } }
            val clip = track?.clips?.find { it.id == clipId }
            val adjustments = clip?.adjustments ?: com.example.model.adjustments.AdjustmentStack("blank", com.example.model.adjustments.TargetType.CLIP, "blank")
            
            val bitmap = timelinePreviewController.extractCurrentFrameBitmap()
            if (bitmap != null) {
                scopeViewModel.analyzeFrame(
                    bitmap, 
                    adjustments.params, 
                    com.example.model.adjustments.ColorPipelineSettings()
                )
            }
        }
    }

    val activeClipAndAsset = remember(playheadFrame, timelineUiState.project.mediaAssets) {
        timelineViewModel.getActiveVideoClipAndAsset()
    }
    val isProxyActive = activeClipAndAsset?.second?.proxyStatus == com.example.timeline.media.ProxyStatus.READY

    var lutSizeToBake by remember { mutableStateOf(33) }
    
    val coroutineScope = rememberCoroutineScope()
    val exportLutLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val params = colorAdjustmentViewModel.evaluateCurrentFrameAdjustments()
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val cr = context.contentResolver
                    cr.openOutputStream(uri)?.use { stream ->
                        com.example.model.colorgrade.lut.LutBakeEngine.bakeGradeToCubeStream(
                            params, lutSizeToBake, stream, "ChromaPro_Grade"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (showExportDialog) {
        com.example.ui.export.ExportScreen(
            project = timelineUiState.project,
            onDismiss = { showExportDialog = false },
            onExportLut = { size ->
                lutSizeToBake = size
                showExportDialog = false
                exportLutLauncher.launch("chromapro_LUT_${size}x${size}x${size}.cube")
            }
        )
    }

    Scaffold(
        topBar = {
            if (!timelineUiState.showProjectSetup && !showAdvancedTimeline) {
                com.example.ui.EditorTopBar(
                    settings = timelineUiState.project.settings,
                    isProxyActive = isProxyActive,
                    onExportClick = { showExportDialog = true },
                    onDebugClick = { timelineViewModel.toggleDebugPanel() },
                    onToggleScopes = { showScopes = !showScopes }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val proxyProgress by timelineViewModel.proxyProgress.collectAsState()
            var showSafeArea by remember { mutableStateOf(false) }
            var showGuideOverlay by remember { mutableStateOf(false) }

            val launchers = com.example.timeline.ui.rememberMediaImportLaunchers(
                onUrisPicked = { uris -> timelineViewModel.handlePickedUris(uris) }
            )

            var relinkAssetId by remember { mutableStateOf<String?>(null) }
            val relinkLauncher = androidx.activity.compose.rememberLauncherForActivityResult(com.example.timeline.media.MediaPickerContract.OpenMultiple) { uris ->
                relinkAssetId?.let { id ->
                    uris.firstOrNull()?.let { uri ->
                        timelineViewModel.relinkMedia(id, uri)
                    }
                }
                relinkAssetId = null
            }

            if (smokeTestVideoPath != null) {
                com.example.ui.VlcSmokeTestScreen(
                    videoPath = smokeTestVideoPath!!,
                    onBack = { smokeTestVideoPath = null }
                )
                return@Scaffold
            }

            if (timelineUiState.showProjectSetup) {
                com.example.timeline.ui.NewProjectScreen(
                    onCreateProject = { settings ->
                        timelineViewModel.createNewProject(settings)
                    },
                    onCancel = { }
                )
                return@Scaffold
            }

            if (timelineUiState.showDebugPanel) {
                val engine = timelinePreviewController.previewEngine
                val diagnostics = mapOf(
                    "activeEngine" to if (engine.currentError.collectAsState().value != null) "STILL_FRAME_FALLBACK" else engine.engineType.name,
                    "libVlcAvailable" to runCatching { Class.forName("org.videolan.libvlc.LibVLC"); "true" }.getOrDefault("false"),
                    "nativeFfmpegAvailable" to "false",
                    "media3FallbackUsed" to (engine.engineType.name == "MEDIA3_FALLBACK").toString(),
                    "firstFrameRendered" to engine.firstFrameRendered.collectAsState().value.toString(),
                    "lastError" to (engine.currentError.collectAsState().value ?: "none")
                )
                com.example.ui.DebugPanel(
                    assets = timelineUiState.project.mediaAssets,
                    clipsCount = timelineUiState.project.tracks.sumOf { it.clips.size },
                    playheadFrame = playheadFrame,
                    diagnostics = diagnostics,
                    onTestVlc = { path -> smokeTestVideoPath = path }
                )
            }

            if (showAdvancedTimeline) {
                com.example.timeline.ui.AdvancedTimelineRoute(
                    timelineViewModel = timelineViewModel,
                    editorViewModel = editorViewModel,
                    timelinePreviewController = timelinePreviewController,
                    onRelinkRequest = { id -> relinkAssetId = id; relinkLauncher.launch(arrayOf("video/*", "image/*", "audio/*")) },
                    onClose = { showAdvancedTimeline = false }
                )
                return@Scaffold
            }

            com.example.ui.editor.EditorScreen(
                timelineViewModel = timelineViewModel,
                colorAdjustmentViewModel = colorAdjustmentViewModel,
                colorMatchViewModel = colorMatchViewModel,
                scopeViewModel = scopeViewModel,
                mainViewModel = viewModel,
                editorViewModel = editorViewModel,
                timelinePreviewController = timelinePreviewController,
                onExportClick = { showExportDialog = true },
                onDebugClick = { timelineViewModel.toggleDebugPanel() },
                showScopes = showScopes,
                onToggleScopes = { showScopes = !showScopes },
                launchers = launchers,
                relinkLauncher = relinkLauncher,
                onRelinkRequest = { id -> 
                    relinkAssetId = id
                    relinkLauncher.launch(arrayOf("*/*"))
                },
                onOpenAdvancedTimeline = { 
                    com.example.timeline.core.ActiveProjectManager.activeProject = timelineUiState.project
                    com.example.timeline.core.ActiveProjectManager.playheadFrame = playheadFrame
                    com.example.timeline.core.ActiveProjectManager.selectedClipId = timelineUiState.selectedClipId
                    com.example.timeline.core.ActiveProjectManager.isDirty = false
                    
                    timelineViewModel.forceAutosaveProject()
                    val intent = android.content.Intent(context, com.example.timeline.ui.AdvancedTimelineActivity::class.java)
                    context.startActivity(intent)
                }
            )
        }
    }
}



// Removed fake Timeline implementation

