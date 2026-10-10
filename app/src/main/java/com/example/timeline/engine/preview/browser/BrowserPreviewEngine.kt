package com.example.timeline.engine.preview.browser

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.engine.preview.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class BrowserPreviewEngine(val context: Context) : PreviewEngine {
    override val engineType = PreviewEngineType.BROWSER

    override val capabilities: PreviewEngineCapabilities = PreviewEngineCapabilities(
        canPlayMovingVideo = true,
        canScrub = true,
        canRenderFirstFrame = true,
        canApplyRealtimeColorGrade = false,
        canApplyBasicAdjustments = false,
        canUseProxy = false,
        currentFailureReason = null
    )
    
    private val _currentState = MutableStateFlow(PreviewState.IDLE)
    override val currentState: StateFlow<PreviewState> = _currentState.asStateFlow()
    
    private val _currentError = MutableStateFlow<String?>(null)
    override val currentError: StateFlow<String?> = _currentError.asStateFlow()
    
    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()
    
    val webView = WebView(context)
    private var _isPlaying = false
    private var _currentPositionUs = 0L
    private var _durationUs = 0L
    
    override var onTimeChanged: ((Long) -> Unit)? = null

    init {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        webView.addJavascriptInterface(this, "AndroidWebView")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = android.webkit.WebChromeClient()
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000000; }
                    video { width: 100%; height: 100%; object-fit: contain; background: #000000; }
                </style>
            </head>
            <body>
                <video id="vid" playsinline preload="auto"></video>
                <script>
                    const vid = document.getElementById('vid');
                    function updateFirstFrame() {
                        AndroidWebView.onEvent('firstframe', 0);
                    }
                    vid.addEventListener('loadeddata', () => { 
                        AndroidWebView.onEvent('loadeddata', 0);
                        if ('requestVideoFrameCallback' in vid) {
                            vid.requestVideoFrameCallback(updateFirstFrame);
                        } else {
                            setTimeout(updateFirstFrame, 150); // Fallback
                        }
                    });
                    vid.addEventListener('loadedmetadata', () => { AndroidWebView.onEvent('loadedmetadata', vid.duration); });
                    vid.addEventListener('canplay', () => { AndroidWebView.onEvent('canplay', 0); });
                    vid.addEventListener('playing', () => { AndroidWebView.onEvent('playing', 0); });
                    vid.addEventListener('pause', () => { AndroidWebView.onEvent('pause', 0); });
                    vid.addEventListener('seeked', () => { AndroidWebView.onEvent('seeked', 0); });
                    vid.addEventListener('ended', () => { AndroidWebView.onEvent('ended', 0); });
                    vid.addEventListener('error', (e) => { 
                        var errMessage = 'Unknown Error';
                        if(vid.error) {
                            errMessage = 'Code ' + vid.error.code + ': ' + vid.error.message;
                        }
                        AndroidWebView.onError(errMessage); 
                    });
                    setInterval(() => {
                        AndroidWebView.onTimeUpdate(vid.currentTime);
                    }, 50);
                </script>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL("file:///localhost/", html, "text/html", "UTF-8", null)
    }

    @JavascriptInterface
    fun onEvent(event: String, value: Double) {
        Handler(Looper.getMainLooper()).post {
            when (event) {
                "firstframe" -> { _firstFrameRendered.value = true }
                "loadedmetadata" -> { _durationUs = (value * 1000000).toLong() }
                "canplay" -> { _currentState.value = PreviewState.READY }
                "playing" -> { _isPlaying = true; _currentState.value = PreviewState.PLAYING }
                "pause" -> { _isPlaying = false; _currentState.value = PreviewState.READY }
                "ended" -> { _isPlaying = false; _currentState.value = PreviewState.READY }
            }
        }
    }
    
    @JavascriptInterface
    fun onError(err: String) {
        Handler(Looper.getMainLooper()).post {
            _currentError.value = err
            _currentState.value = PreviewState.ERROR
        }
    }
    
    @JavascriptInterface
    fun onTimeUpdate(timeSec: Double) {
        _currentPositionUs = (timeSec * 1000000).toLong()
        Handler(Looper.getMainLooper()).post {
            onTimeChanged?.invoke(_currentPositionUs)
        }
    }

    override fun setSurface(surface: Surface?) {
        // WebView renders into its own hierarchy
    }

    override fun loadMedia(uriString: String, useProxy: Boolean, sourcePositionMs: Long, evaluatedParams: VideoAdjustmentParams?, presentationTimeUs: Long) {
        _currentState.value = PreviewState.LOADING
        _firstFrameRendered.value = false
        
        // Need object URL mapping if it's content:// or blob
        // But for local files we can just use file:// if WebView allows
        // AI Studio usually runs in app, so file is copied locally
        var finalUrl = uriString
        if (finalUrl.startsWith("/")) {
            finalUrl = "file://$finalUrl"
        }
        
        Handler(Looper.getMainLooper()).post {
            val js = "document.getElementById('vid').src = '$finalUrl'; document.getElementById('vid').currentTime = ${sourcePositionMs / 1000.0};"
            webView.evaluateJavascript(js, null)
        }
    }

    override fun play() {
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript("document.getElementById('vid').play();", null)
        }
    }

    override fun pause() {
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript("document.getElementById('vid').pause();", null)
        }
    }

    override fun seekTo(timeUs: Long) {
        Handler(Looper.getMainLooper()).post {
            val sec = timeUs / 1000000.0
            webView.evaluateJavascript("document.getElementById('vid').currentTime = $sec;", null)
            _currentPositionUs = timeUs
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript("document.getElementById('vid').playbackRate = $speed;", null)
        }
    }

    override fun setResolutionFallback(resolutionMode: String) {
    }

    override fun release() {
        Handler(Looper.getMainLooper()).post {
            webView.destroy()
        }
    }

    override val currentPositionUs: Long get() = _currentPositionUs
    override val durationUs: Long get() = _durationUs
    override val isPlaying: Boolean get() = _isPlaying
}
