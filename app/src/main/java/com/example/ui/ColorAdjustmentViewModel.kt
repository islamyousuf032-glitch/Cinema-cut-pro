package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.commands.SetAdjustmentParameterCommand
import com.example.timeline.core.TimelineProject
import com.example.timeline.ui.TimelineViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.model.colorgrade.GradeAutosaveHook
import com.example.model.colorgrade.commands.*
import com.example.model.colorgrade.ColorGradeStack
import com.example.model.colorgrade.GradeKeyframeManager

class ColorAdjustmentViewModel(
    private val timelineViewModel: TimelineViewModel
) : ViewModel() {

    private var dragStartGradeStack: ColorGradeStack? = null
    
    // UI state flows from timelineViewModel
    val dragInProgress = MutableStateFlow(false)
    val isEyedropperActive = MutableStateFlow(false)

    fun toggleEyedropper() {
        isEyedropperActive.value = !isEyedropperActive.value
    }

    fun applyEyedropperColor(r: Float, g: Float, b: Float) {
        val hsl = FloatArray(3)
        com.example.model.colorgrade.engine.ColorMath.rgb2hsl(r, g, b, hsl)
        
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        if (clipIndex != -1) {
            val clip = track.clips[clipIndex]
            val grade = clip.colorGrade
            val newHsl = grade.hslAdjustments.copy(
                enabled = true,
                hueCenter = hsl[0],
                saturationMin = (hsl[1] - 0.1f).coerceAtLeast(0f),
                saturationMax = (hsl[1] + 0.1f).coerceAtMost(1f),
                luminanceMin = (hsl[2] - 0.1f).coerceAtLeast(0f),
                luminanceMax = (hsl[2] + 0.1f).coerceAtMost(1f)
            )
            
            beginAdjustmentDrag()
            updateColorGradeLive(grade.copy(hslAdjustments = newHsl))
            endAdjustmentDrag("Eyedropper Sample")
            isEyedropperActive.value = false
        }
    }

    fun beginAdjustmentDrag() {
        if (dragStartGradeStack == null) {
            val tcId = timelineViewModel.uiState.value.selectedClipId
            dragStartGradeStack = timelineViewModel.uiState.value.project.tracks.flatMap { it.clips }.find { it.id == tcId }?.colorGrade
            dragInProgress.value = true
        }
    }

    fun endAdjustmentDrag(actionType: String = "ColorGradeCommand") {
        val oldGrade = dragStartGradeStack
        val targetClipId = timelineViewModel.uiState.value.selectedClipId
        val newGrade = timelineViewModel.uiState.value.project.tracks.flatMap { t -> t.clips }.find { c -> c.id == targetClipId }?.colorGrade
        
        if (oldGrade != null && targetClipId != null && newGrade != null && oldGrade != newGrade) {
            val cmd = when (actionType) {
                "SetColorWheelCommand" -> SetColorWheelCommand(targetClipId, oldGrade, newGrade)
                "SetCurveCommand" -> SetCurveCommand(targetClipId, oldGrade, newGrade)
                "AddCurvePointCommand" -> AddCurvePointCommand(targetClipId, oldGrade, newGrade)
                "RemoveCurvePointCommand" -> RemoveCurvePointCommand(targetClipId, oldGrade, newGrade)
                "ApplyLutCommand" -> ApplyLutCommand(targetClipId, oldGrade, newGrade)
                "RemoveLutCommand" -> RemoveLutCommand(targetClipId, oldGrade, newGrade)
                "SetHslQualifierCommand" -> SetHslQualifierCommand(targetClipId, oldGrade, newGrade)
                "ApplyColorMatchCommand" -> ApplyColorMatchCommand(targetClipId, oldGrade, newGrade)
                "SetSkinProtectionCommand" -> SetSkinProtectionCommand(targetClipId, oldGrade, newGrade)
                else -> ColorGradeCommand(targetClipId, oldGrade, newGrade)
            }
            timelineViewModel.pushHistoryCommand(cmd)
        }
        dragStartGradeStack = null
        dragInProgress.value = false
        
        GradeAutosaveHook.onGestureEnded(viewModelScope) {
            timelineViewModel.forceSaveProject()
        }
    }

    fun selectAdjustmentTarget(targetType: com.example.model.adjustments.TargetType, targetId: String) {
        // Implementation stub
    }

    fun getSelectedClipAdjustmentStack(): AdjustmentStack? {
        val selectedClipId = timelineViewModel.uiState.value.selectedClipId ?: return null
        return timelineViewModel.uiState.value.project.tracks.flatMap { it.clips }.find { it.id == selectedClipId }?.adjustments
    }

    private var clipboardAdjustments: AdjustmentStack? = null

    fun copyAdjustmentsFromSelectedClip() {
        clipboardAdjustments = getSelectedClipAdjustmentStack()?.copy()
    }

    fun pasteAdjustmentsToSelectedClip() {
        val stack = clipboardAdjustments ?: return
        val currentStack = getSelectedClipAdjustmentStack() ?: return
        
        beginAdjustmentDrag()
        
        val newStack = currentStack.copy(
            params = stack.params.copy()
        )
        // Also apply the colorGrade which maps to params
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val sourceClip = state.project.tracks.flatMap { it.clips }.find { it.adjustments.stackId == stack.stackId }
        val newGrade = sourceClip?.colorGrade?.copy() ?: com.example.model.colorgrade.ColorGradeHelper.defaultColorGrade(clipId)
        
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        if (clipIndex != -1) {
            val oldClip = track.clips[clipIndex]
            val newClip = oldClip.copy(adjustments = newStack, colorGrade = newGrade)
            val newTracks = state.project.tracks.map {
                if (it.id == track.id) {
                    val clipsMutable = it.clips.toMutableList()
                    clipsMutable[clipIndex] = newClip
                    it.copy(clips = clipsMutable)
                } else it
            }
            timelineViewModel.updateStateWithoutHistory(state.project.copy(tracks = newTracks))
        }
        
        endAdjustmentDrag("Paste Grade")
    }

    fun saveAdjustmentPreset(name: String) {
        val stack = getSelectedClipGrade() ?: return
        com.example.model.colorgrade.ColorGradePresetManager.savePreset(name, "", stack)
    }

    fun applyAdjustmentPreset(presetId: String) {
        val targetClipId = timelineViewModel.uiState.value.selectedClipId ?: return
        val presets = com.example.model.colorgrade.ColorGradePresetManager.presets.value.flatMap { it.presets }
        val preset = presets.find { it.presetId == presetId } ?: return
        val currentGrade = getSelectedClipGrade() ?: return
        
        val newGrade = com.example.model.colorgrade.ColorGradeHelper.applyPreset(currentGrade, preset)
        beginAdjustmentDrag()
        updateColorGradeLive(newGrade)
        endAdjustmentDrag("ApplyPresetCommand")
    }

    private fun getSelectedClipGrade(): ColorGradeStack? {
        val selectedClipId = timelineViewModel.uiState.value.selectedClipId ?: return null
        return timelineViewModel.uiState.value.project.tracks.flatMap { it.clips }.find { it.id == selectedClipId }?.colorGrade
    }

    fun addOrUpdateKeyframe(parameterId: String, frame: Long, value: Float) {
        val grade = getSelectedClipGrade() ?: return
        beginAdjustmentDrag()
        val newGrade = GradeKeyframeManager.addOrUpdateKeyframe(grade, parameterId, frame, value)
        updateColorGradeLive(newGrade)
        endAdjustmentDrag("AddKeyframeCommand")
    }

    fun removeKeyframe(parameterId: String, frame: Long) {
        val grade = getSelectedClipGrade() ?: return
        beginAdjustmentDrag()
        val newGrade = GradeKeyframeManager.removeKeyframe(grade, parameterId, frame)
        updateColorGradeLive(newGrade)
        endAdjustmentDrag("RemoveKeyframeCommand")
    }

    fun toggleBeforeAfter() {
        // Handled directly via UI state temporarily, but can be maintained here
    }

    fun createAdjustmentLayer() {
        val state = timelineViewModel.uiState.value
        val playheadFrame = timelineViewModel.playheadFrame.value
        val newClipId = java.util.UUID.randomUUID().toString()
        
        val newClip = com.example.timeline.core.TimelineClip(
            id = newClipId,
            name = "Adjustment Layer",
            trackId = "",
            mediaId = "adjustment_layer_internal",
            type = com.example.timeline.core.ClipType.ADJUSTMENT,
            timelineStart = playheadFrame,
            sourceIn = 0,
            sourceOut = 100,
            adjustments = AdjustmentStack(java.util.UUID.randomUUID().toString(), com.example.model.adjustments.TargetType.CLIP, newClipId)
        )
        
        var adjustmentTrack = state.project.tracks.find { it.type == com.example.timeline.core.TrackType.ADJUSTMENT }
        val newTracks = state.project.tracks.toMutableList()
        if (adjustmentTrack == null) {
            val newTrack = com.example.timeline.core.TimelineTrack(
                name = "Adjustment Track",
                type = com.example.timeline.core.TrackType.ADJUSTMENT,
                clips = emptyList()
            )
            val updatedClip = newClip.copy(trackId = newTrack.id)
            val newTrackWithClip = newTrack.copy(clips = listOf(updatedClip))
            newTracks.add(newTrackWithClip)
        } else {
            val updatedClip = newClip.copy(trackId = adjustmentTrack.id)
            val trackIndex = newTracks.indexOf(adjustmentTrack)
            val updatedTrack = adjustmentTrack.copy(clips = adjustmentTrack.clips + updatedClip)
            newTracks[trackIndex] = updatedTrack
        }
        
        val newProject = state.project.copy(tracks = newTracks)
        timelineViewModel.updateStateWithoutHistory(newProject) // or pushHistoryCommand based on design
    }

    fun applyAdjustmentToTrack() {
        // Implementation stub
    }

    fun evaluateCurrentFrameAdjustments(): com.example.model.adjustments.VideoAdjustmentParams {
        val selectedClipId = timelineViewModel.uiState.value.selectedClipId ?: return com.example.model.adjustments.VideoAdjustmentParams.default()
        val clip = timelineViewModel.uiState.value.project.tracks.flatMap { it.clips }.find { it.id == selectedClipId } ?: return com.example.model.adjustments.VideoAdjustmentParams.default()
        
        val evaluatedGrade = clip.colorGrade.evaluateGradeAtFrame(timelineViewModel.playheadFrame.value)
        // Ensure the preview uses the evaluated grade
        // We will return the mapped params
        val newGrade = evaluatedGrade
        return com.example.model.adjustments.VideoAdjustmentParams.default().copy(
                lift = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.lift.r, newGrade.primaryCorrections.lift.g, newGrade.primaryCorrections.lift.b),
                gamma = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.gamma.r + 1f, newGrade.primaryCorrections.gamma.g + 1f, newGrade.primaryCorrections.gamma.b + 1f),
                gain = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.gain.r + 1f, newGrade.primaryCorrections.gain.g + 1f, newGrade.primaryCorrections.gain.b + 1f),
                offsetWheel = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.offset.r, newGrade.primaryCorrections.offset.g, newGrade.primaryCorrections.offset.b),
                shadowColorBalance = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.shadows.r, newGrade.primaryCorrections.shadows.g, newGrade.primaryCorrections.shadows.b),
                midtoneColorBalance = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.midtones.r, newGrade.primaryCorrections.midtones.g, newGrade.primaryCorrections.midtones.b),
                highlightColorBalance = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.highlights.r, newGrade.primaryCorrections.highlights.g, newGrade.primaryCorrections.highlights.b),
                hslHue = floatArrayOf(
                    newGrade.selectiveColor.red.hueShift, newGrade.selectiveColor.orange.hueShift, newGrade.selectiveColor.yellow.hueShift, newGrade.selectiveColor.green.hueShift,
                    newGrade.selectiveColor.cyan.hueShift, newGrade.selectiveColor.blue.hueShift, newGrade.selectiveColor.purple.hueShift, newGrade.selectiveColor.magenta.hueShift
                ),
                hslSat = floatArrayOf(
                    newGrade.selectiveColor.red.saturation, newGrade.selectiveColor.orange.saturation, newGrade.selectiveColor.yellow.saturation, newGrade.selectiveColor.green.saturation,
                    newGrade.selectiveColor.cyan.saturation, newGrade.selectiveColor.blue.saturation, newGrade.selectiveColor.purple.saturation, newGrade.selectiveColor.magenta.saturation
                ),
                hslLum = floatArrayOf(
                    newGrade.selectiveColor.red.luminance, newGrade.selectiveColor.orange.luminance, newGrade.selectiveColor.yellow.luminance, newGrade.selectiveColor.green.luminance,
                    newGrade.selectiveColor.cyan.luminance, newGrade.selectiveColor.blue.luminance, newGrade.selectiveColor.purple.luminance, newGrade.selectiveColor.magenta.luminance
                ),
                qualEnabled = newGrade.hslAdjustments.enabled,
                qualHueCenter = newGrade.hslAdjustments.hueCenter,
                qualHueWidth = newGrade.hslAdjustments.hueWidth,
                qualHueFeather = newGrade.hslAdjustments.hueFeather,
                qualSatMin = newGrade.hslAdjustments.saturationMin,
                qualSatMax = newGrade.hslAdjustments.saturationMax,
                qualSatFeather = newGrade.hslAdjustments.saturationFeather,
                qualLumMin = newGrade.hslAdjustments.luminanceMin,
                qualLumMax = newGrade.hslAdjustments.luminanceMax,
                qualLumFeather = newGrade.hslAdjustments.luminanceFeather,
                qualInvert = newGrade.hslAdjustments.invertMask,
                qualShowMatte = newGrade.hslAdjustments.showMatte,
                qualHueShift = newGrade.hslAdjustments.hueShift,
                qualSat = newGrade.hslAdjustments.saturation,
                qualLum = newGrade.hslAdjustments.luminance,
                qualContrast = newGrade.hslAdjustments.contrast,
                qualTemp = newGrade.hslAdjustments.temperature,
                qualTint = newGrade.hslAdjustments.tint
        )
    }

    fun updateAdjustmentParamLive(parameterId: String, value: Float) {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        
        if (clipIndex != -1) {
            val oldClip = track.clips[clipIndex]
            val newStack = oldClip.adjustments.updateParam(parameterId, value)
            val newClip = oldClip.copy(adjustments = newStack)
            val newTracks = state.project.tracks.map {
                if (it.id == track.id) {
                    val clipsMutable = it.clips.toMutableList()
                    clipsMutable[clipIndex] = newClip
                    it.copy(clips = clipsMutable)
                } else {
                    it
                }
            }
            val newProject = state.project.copy(tracks = newTracks)
            timelineViewModel.updateStateWithoutHistory(newProject)
        }
    }

    fun setLutId(lutId: String?) {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        beginAdjustmentDrag()
        
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        
        if (clipIndex != -1) {
            val oldClip = track.clips[clipIndex]
            val newParams = oldClip.adjustments.params.copy(lutId = lutId)
            val newStack = oldClip.adjustments.copy(params = newParams)
            val newClip = oldClip.copy(adjustments = newStack)
            val newTracks = state.project.tracks.map {
                if (it.id == track.id) {
                    val clipsMutable = it.clips.toMutableList()
                    clipsMutable[clipIndex] = newClip
                    it.copy(clips = clipsMutable)
                } else {
                    it
                }
            }
            val newProject = state.project.copy(tracks = newTracks)
            timelineViewModel.updateStateWithoutHistory(newProject)
        }
        endAdjustmentDrag("Set LUT")
    }

    fun updateColorGradeLive(
        newGrade: com.example.model.colorgrade.ColorGradeStack,
        colorLayerId: String? = null
    ) {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        
        if (clipIndex != -1) {
            val oldClip = track.clips[clipIndex]
            
            // Map ColorGradeStack -> VideoAdjustmentParams to affect GPU preview/export immediately
            val currentParams = oldClip.adjustments.params
            val newParams = currentParams.copy(
                lift = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.lift.r, newGrade.primaryCorrections.lift.g, newGrade.primaryCorrections.lift.b),
                gamma = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.gamma.r + 1f, newGrade.primaryCorrections.gamma.g + 1f, newGrade.primaryCorrections.gamma.b + 1f),
                gain = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.gain.r + 1f, newGrade.primaryCorrections.gain.g + 1f, newGrade.primaryCorrections.gain.b + 1f),
                offsetWheel = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.offset.r, newGrade.primaryCorrections.offset.g, newGrade.primaryCorrections.offset.b),
                shadowColorBalance = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.shadows.r, newGrade.primaryCorrections.shadows.g, newGrade.primaryCorrections.shadows.b),
                midtoneColorBalance = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.midtones.r, newGrade.primaryCorrections.midtones.g, newGrade.primaryCorrections.midtones.b),
                highlightColorBalance = com.example.model.adjustments.Vector3(newGrade.primaryCorrections.highlights.r, newGrade.primaryCorrections.highlights.g, newGrade.primaryCorrections.highlights.b),
                hslHue = floatArrayOf(
                    newGrade.selectiveColor.red.hueShift, newGrade.selectiveColor.orange.hueShift, newGrade.selectiveColor.yellow.hueShift, newGrade.selectiveColor.green.hueShift,
                    newGrade.selectiveColor.cyan.hueShift, newGrade.selectiveColor.blue.hueShift, newGrade.selectiveColor.purple.hueShift, newGrade.selectiveColor.magenta.hueShift
                ),
                hslSat = floatArrayOf(
                    newGrade.selectiveColor.red.saturation, newGrade.selectiveColor.orange.saturation, newGrade.selectiveColor.yellow.saturation, newGrade.selectiveColor.green.saturation,
                    newGrade.selectiveColor.cyan.saturation, newGrade.selectiveColor.blue.saturation, newGrade.selectiveColor.purple.saturation, newGrade.selectiveColor.magenta.saturation
                ),
                hslLum = floatArrayOf(
                    newGrade.selectiveColor.red.luminance, newGrade.selectiveColor.orange.luminance, newGrade.selectiveColor.yellow.luminance, newGrade.selectiveColor.green.luminance,
                    newGrade.selectiveColor.cyan.luminance, newGrade.selectiveColor.blue.luminance, newGrade.selectiveColor.purple.luminance, newGrade.selectiveColor.magenta.luminance
                ),
                qualEnabled = newGrade.hslAdjustments.enabled,
                qualHueCenter = newGrade.hslAdjustments.hueCenter,
                qualHueWidth = newGrade.hslAdjustments.hueWidth,
                qualHueFeather = newGrade.hslAdjustments.hueFeather,
                qualSatMin = newGrade.hslAdjustments.saturationMin,
                qualSatMax = newGrade.hslAdjustments.saturationMax,
                qualSatFeather = newGrade.hslAdjustments.saturationFeather,
                qualLumMin = newGrade.hslAdjustments.luminanceMin,
                qualLumMax = newGrade.hslAdjustments.luminanceMax,
                qualLumFeather = newGrade.hslAdjustments.luminanceFeather,
                qualInvert = newGrade.hslAdjustments.invertMask,
                qualShowMatte = newGrade.hslAdjustments.showMatte,
                qualHueShift = newGrade.hslAdjustments.hueShift,
                qualSat = newGrade.hslAdjustments.saturation,
                qualLum = newGrade.hslAdjustments.luminance,
                qualContrast = newGrade.hslAdjustments.contrast,
                qualTemp = newGrade.hslAdjustments.temperature,
                qualTint = newGrade.hslAdjustments.tint,
                exposureStops = newGrade.primaryCorrections.exposure,
                contrast = newGrade.primaryCorrections.contrast,
                saturation = newGrade.primaryCorrections.saturation,
                temperature = newGrade.primaryCorrections.temperature,
                tint = newGrade.primaryCorrections.tint
            )
            val newAdjustments = oldClip.adjustments.copy(params = newParams)
            
            val newColorLayers = colorLayerId?.let { targetLayerId ->
                oldClip.colorLayers.copy(
                    layers = oldClip.colorLayers.layers.map { layer ->
                        if (layer.id == targetLayerId) layer.copy(grade = newGrade) else layer
                    }
                )
            } ?: oldClip.colorLayers
            val newClip = oldClip.copy(
                colorGrade = newGrade,
                adjustments = newAdjustments,
                colorLayers = newColorLayers
            )
            val newTracks = state.project.tracks.map {
                if (it.id == track.id) {
                    val clipsMutable = it.clips.toMutableList()
                    clipsMutable[clipIndex] = newClip
                    it.copy(clips = clipsMutable)
                } else {
                    it
                }
            }
            val newProject = state.project.copy(tracks = newTracks)
            timelineViewModel.updateStateWithoutHistory(newProject)
            GradeAutosaveHook.onGradeChanged(viewModelScope) {
                timelineViewModel.forceSaveProject()
            }
        }
    }

    fun updateAdjustmentParam(parameterId: String, value: Float) {
        beginAdjustmentDrag()
        updateAdjustmentParamLive(parameterId, value)
        endAdjustmentDrag(parameterId)
    }

    fun resetAdjustmentParam(parameterId: String) {
        val currentState = timelineViewModel.uiState.value.project
        val targetClipId = timelineViewModel.uiState.value.selectedClipId ?: return
        
        beginAdjustmentDrag()
        
        val state = timelineViewModel.uiState.value
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == targetClipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == targetClipId }
        
        if (clipIndex != -1) {
            val oldClip = track.clips[clipIndex]
            val defaultParams = com.example.model.adjustments.VideoAdjustmentParams.default()
            
            // Helper to pull default value correctly
            val defaultValue = when(parameterId) {
                "liftR", "liftG", "liftB" -> 0f
                "gammaR", "gammaG", "gammaB" -> 1f
                "gainR", "gainG", "gainB" -> 1f
                "offsetR", "offsetG", "offsetB" -> 0f
                "skinToneProtection" -> 0f
                "shadowBalanceR", "shadowBalanceG", "shadowBalanceB" -> 0f
                "midtoneBalanceR", "midtoneBalanceG", "midtoneBalanceB" -> 0f
                "highlightBalanceR", "highlightBalanceG", "highlightBalanceB" -> 0f
                "saturation" -> 1f
                "redChannelMultiplier", "greenChannelMultiplier", "blueChannelMultiplier" -> 1f
                else -> 0f // All other typical slider defaults are 0f
            }
            
            val newStack = oldClip.adjustments.updateParam(parameterId, defaultValue)
            val newClip = oldClip.copy(adjustments = newStack)
            val newTracks = state.project.tracks.map {
                if (it.id == track.id) {
                    val clipsMutable = it.clips.toMutableList()
                    clipsMutable[clipIndex] = newClip
                    it.copy(clips = clipsMutable)
                } else {
                    it
                }
            }
            val newProject = state.project.copy(tracks = newTracks)
            timelineViewModel.updateStateWithoutHistory(newProject)
        }
        
        endAdjustmentDrag(parameterId)
    }

    fun resetAllAdjustments() {
        val currentState = timelineViewModel.uiState.value.project
        val targetClipId = timelineViewModel.uiState.value.selectedClipId ?: return
        
        beginAdjustmentDrag()
        val defaultStack = AdjustmentStack("default", com.example.model.adjustments.TargetType.CLIP, targetClipId)
        
        val state = timelineViewModel.uiState.value
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == targetClipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == targetClipId }
        
        if (clipIndex != -1) {
            val oldClip = track.clips[clipIndex]
            val newClip = oldClip.copy(
                adjustments = defaultStack,
                colorGrade = oldClip.colorGrade.resetAllGrade()
            )
            val newTracks = state.project.tracks.map {
                if (it.id == track.id) {
                    val clipsMutable = it.clips.toMutableList()
                    clipsMutable[clipIndex] = newClip
                    it.copy(clips = clipsMutable)
                } else {
                    it
                }
            }
            val newProject = state.project.copy(tracks = newTracks)
            timelineViewModel.updateStateWithoutHistory(newProject)
        }
        endAdjustmentDrag("All Adjustments")
    }
}
