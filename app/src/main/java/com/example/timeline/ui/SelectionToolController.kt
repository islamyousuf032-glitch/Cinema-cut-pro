package com.example.timeline.ui

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.MoveClipCommand
import com.example.ui.editor.EditorViewModel

class SelectionToolController(
    private val timelineViewModel: TimelineViewModel,
    private val editorViewModel: EditorViewModel
) {

    private val linkedSelectionController = LinkedSelectionController()

    fun handleTap(clipId: String?, isMultiSelect: Boolean = false, linkedSelectionEnabled: Boolean = false) {
        val currentSelected = editorViewModel.selectedClipId.value
        if (clipId == null) {
            editorViewModel.selectClip(null)
            timelineViewModel.selectClip(null)
        } else {
            val project = timelineViewModel.uiState.value.project
            val clipsToSelect = linkedSelectionController.getLinkedClips(clipId, project, linkedSelectionEnabled)
            
            // For now, editorViewModel only supports single select easily, but timelineViewModel supports multi
            editorViewModel.selectClip(clipId)
            timelineViewModel.updateSelection(clipsToSelect)
        }
    }

    fun handleDragEnd(
        clipId: String,
        originalStart: Long,
        newStart: Long,
        trackId: String,
        linkedSelectionEnabled: Boolean = false
    ) {
        if (originalStart != newStart) {
            val project = timelineViewModel.uiState.value.project
            val track = project.tracks.find { it.id == trackId }
            if (track?.isLocked == true) return
            
            val delta = newStart - originalStart
            
            val clipsToMove = if (linkedSelectionEnabled) {
                linkedSelectionController.getLinkedClips(clipId, project, true)
            } else {
                setOf(clipId)
            }
            
            val commands = mutableListOf<com.example.timeline.core.TimelineCommand>()
            for (cId in clipsToMove) {
                val cTrack = project.tracks.find { it.clips.any { c -> c.id == cId } } ?: continue
                if (cTrack.isLocked) continue
                val cClip = cTrack.clips.find { it.id == cId } ?: continue
                val cNewStart = cClip.timelineStart + delta
                
                val isValid = com.example.timeline.core.TimelineValidator.isValidMove(project, cId, cTrack.id, cNewStart)
                if (isValid) {
                    commands.add(com.example.timeline.core.MoveClipCommand(cId, cClip.timelineStart, cNewStart, cTrack.id))
                }
            }
            
            if (commands.isNotEmpty()) {
                if (commands.size == 1) {
                    timelineViewModel.executeCommand(commands.first())
                } else {
                    timelineViewModel.executeCommand(com.example.timeline.core.CompositeTimelineCommand("Move Linked Clips", commands))
                }
            }
        }
    }
    }
