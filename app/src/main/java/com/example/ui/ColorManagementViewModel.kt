package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.adjustments.LogProfile
import com.example.timeline.core.TimelineProject
import com.example.timeline.ui.TimelineViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ColorManagementViewModel(
    private val timelineViewModel: TimelineViewModel
) : ViewModel() {

    fun getSelectedClipProfile(): LogProfile {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return LogProfile.NONE
        val asset = timelineViewModel.getActiveVideoClipAndAsset()?.second ?: return LogProfile.NONE
        val profileName = asset.userAssignedLogProfile ?: return LogProfile.NONE
        return try {
            LogProfile.valueOf(profileName)
        } catch (e: Exception) {
            LogProfile.NONE
        }
    }

    fun assignInputProfile(profile: LogProfile) {
        val state = timelineViewModel.uiState.value
        val clipAndAsset = timelineViewModel.getActiveVideoClipAndAsset() ?: return
        
        val assetId = clipAndAsset.second.assetId
        
        // Find asset in project media assets
        val updatedAssets = state.project.mediaAssets.map {
            if (it.assetId == assetId) {
                it.copy(userAssignedLogProfile = profile.name)
            } else {
                it
            }
        }
        
        val updatedProject = state.project.copy(mediaAssets = updatedAssets)
        timelineViewModel.updateStateWithoutHistory(updatedProject)
        
        // Also update the VideoAdjustmentParams so it takes effect instantly
        updateAdjustmentParam("inputLogProfile", profile)
        
        timelineViewModel.forceSaveProject()
    }
    
    fun updateParamFloat(paramId: String, value: Float) {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        if (clipIndex == -1) return
        
        val oldClip = track.clips[clipIndex]
        val oldParams = oldClip.adjustments.params
        val newParams = when (paramId) {
            "highlightRolloff" -> oldParams.copy(highlightRolloff = value)
            else -> oldParams
        }
        updateClipParams(track, clipIndex, oldClip, newParams, state)
    }

    fun updateParam(paramId: String, value: Any) {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        
        updateAdjustmentParam(paramId, value)
    }
    
    private fun updateAdjustmentParam(paramId: String, value: Any) {
        val state = timelineViewModel.uiState.value
        val clipId = state.selectedClipId ?: return
        
        val track = state.project.tracks.firstOrNull { t -> t.clips.any { c -> c.id == clipId } } ?: return
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        if (clipIndex == -1) return
        
        val oldClip = track.clips[clipIndex]
        val oldParams = oldClip.adjustments.params
        val newParams = when (paramId) {
            "inputLogProfile" -> oldParams.copy(inputLogProfile = value as LogProfile)
            "inputColorSpace" -> oldParams.copy(inputColorSpace = value as com.example.model.adjustments.ColorSpaceProfile)
            "outputColorSpace" -> oldParams.copy(outputColorSpace = value as com.example.model.adjustments.ColorSpaceProfile)
            "toneMappingMode" -> oldParams.copy(toneMappingMode = value as com.example.model.adjustments.engine.ToneMappingMode)
            else -> oldParams
        }
        updateClipParams(track, clipIndex, oldClip, newParams, state)
    }
    
    private fun updateClipParams(
        track: com.example.timeline.core.TimelineTrack, 
        clipIndex: Int, 
        oldClip: com.example.timeline.core.TimelineClip,
        newParams: com.example.model.adjustments.VideoAdjustmentParams,
        state: com.example.timeline.ui.TimelineUiState
    ) {
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
}
