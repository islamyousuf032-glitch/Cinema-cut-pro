package com.example.timeline.media

data class MediaStatusResult(
    val previewStatus: PreviewStatus,
    val proxyStatus: ProxyStatus,
    val report: ImportTechnicalReport
)

class MediaImportDecisionEngine(private val capabilityDetector: CodecCapabilityDetector) {
    fun decideImportStatus(result: MediaAnalysisResult): MediaStatusResult {
        val mimeType = result.defaultMimeType
        var directPlaybackSupport = false
        var proxyRecommendation = false
        var transcodeRecommendation = false
        var pluginRequired = false
        
        var previewStatus = PreviewStatus.DIRECT_SUPPORTED_PROBABLE
        var proxyStatus = ProxyStatus.NONE
        
        val warnings = mutableListOf<String>()
        var userMessage = "Media is ready."

        if (mimeType.startsWith("audio/") || mimeType.startsWith("image/")) {
            directPlaybackSupport = true
            return MediaStatusResult(
                previewStatus = PreviewStatus.DIRECT_SUPPORTED_PROBABLE,
                proxyStatus = ProxyStatus.NONE,
                report = ImportTechnicalReport(
                    detectedFormat = mimeType,
                    directPlaybackSupport = true,
                    proxyRecommendation = false,
                    transcodeRecommendation = false,
                    warnings = warnings,
                    userFriendlyMessage = userMessage
                )
            )
        }

        if (mimeType.contains("raw") || mimeType.contains("braw") || mimeType.contains("r3d") || mimeType.contains("arriraw")) {
            pluginRequired = true
            previewStatus = PreviewStatus.UNSUPPORTED
            proxyStatus = ProxyStatus.REQUIRED // Proxy is only way to preview
            userMessage = "A manufacturer plugin is required to decode this RAW format."
        } else if (mimeType.contains("prores") || mimeType.contains("dnxhr") || mimeType.contains("mxf")) {
            transcodeRecommendation = true
            previewStatus = PreviewStatus.UNSUPPORTED
            proxyStatus = ProxyStatus.REQUIRED
            userMessage = "This professional format requires transcoding for editing on this device."
        } else {
            val isHevc = mimeType.contains("hevc") || mimeType.contains("h265")
            val isAvc = mimeType.contains("avc") || mimeType.contains("h264")
            val isAv1 = mimeType.contains("av01")
            val isVp9 = mimeType.contains("vp9")

            var hardwareSupported = false
            for (videoStream in result.videoStreams) {
                if (capabilityDetector.hasDecoderForFormat(
                        videoStream.codecMime,
                        videoStream.width,
                        videoStream.height,
                        result.frameRate,
                        videoStream.codecProfile,
                        videoStream.codecLevel
                    )) {
                    hardwareSupported = true
                    break
                }
            }

            if (!hardwareSupported) {
                transcodeRecommendation = true
                previewStatus = PreviewStatus.UNSUPPORTED
                proxyStatus = ProxyStatus.REQUIRED
                userMessage = "Your device hardware does not support decoding this format. Transcoding required."
                warnings.add("Hardware decoding not supported for $mimeType")
            } else {
                directPlaybackSupport = true
                val isHighRes = result.width >= 3840 || result.height >= 3840
                val isHighFramerate = result.frameRate > 60
                val isHdr = result.videoStreams.any { it.isHdr }

                if (isHighRes || isHighFramerate || (isHevc && isHdr)) {
                    proxyRecommendation = true
                    proxyStatus = ProxyStatus.RECOMMENDED
                    warnings.add("High resolution or HDR format detected. Proxies are recommended for smooth editing.")
                    userMessage = "Proxies will be generated to ensure smooth playback during editing."
                }
            }
        }

        if (result.isVariableFrameRate) {
            warnings.add("Variable Frame Rate (VFR) detected. Audio sync issues may occur.")
        }

        val report = ImportTechnicalReport(
            detectedFormat = mimeType,
            directPlaybackSupport = directPlaybackSupport,
            proxyRecommendation = proxyRecommendation,
            transcodeRecommendation = transcodeRecommendation,
            warnings = warnings,
            userFriendlyMessage = userMessage
        )

        return MediaStatusResult(previewStatus, proxyStatus, report)
    }
}
