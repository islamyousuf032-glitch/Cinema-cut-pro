package com.example.timeline.engine

import com.example.timeline.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Core engine handling deterministic, frame-accurate timeline mutations.
 * Implements a command pattern history stack (undo/redo) via immutable state copying.
 */
class TimelineEngine(initialState: Timeline) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<Timeline> = _state.asStateFlow()
    
    private val undoStack = mutableListOf<Timeline>()
    private val redoStack = mutableListOf<Timeline>()
    
    private fun applyState(newState: Timeline, saveHistory: Boolean = true) {
        if (saveHistory) {
            undoStack.add(_state.value)
            redoStack.clear()
        }
        _state.value = newState
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_state.value)
            _state.value = undoStack.removeAt(undoStack.size - 1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_state.value)
            _state.value = redoStack.removeAt(redoStack.size - 1)
        }
    }

    // --- Core Operations ---
    
    fun addClip(trackIndex: Int, clip: Clip) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        
        tracks[trackIndex] = track.copy(clips = (track.clips + clip).sortedBy { it.timelineIn })
        applyState(s.copy(tracks = tracks))
    }

    /** Razor Tool: Splits clip exactly at the given frame */
    fun splitClip(trackIndex: Int, clipId: String, playheadFrame: Long) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clip = track.clips.find { it.id == clipId } ?: return
        
        // Cannot split outside the bounds of the clip
        if (playheadFrame <= clip.timelineIn || playheadFrame >= clip.timelineOut) return
        
        val splitOffset = playheadFrame - clip.timelineIn
        val clip1 = clip.copy(sourceOut = clip.sourceIn + splitOffset)
        val clip2 = clip.copy(
            id = UUID.randomUUID().toString(),
            sourceIn = clip.sourceIn + splitOffset,
            timelineIn = playheadFrame
        )
        
        val newClips = track.clips.filter { it.id != clipId } + clip1 + clip2
        tracks[trackIndex] = track.copy(clips = newClips.sortedBy { it.timelineIn })
        applyState(s.copy(tracks = tracks))
    }
    
    /** Ripple Delete: Removes a clip and shifts all subsequent clips left */
    fun rippleDelete(trackIndex: Int, clipId: String, rippleAllTracks: Boolean = false) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clipToRemove = track.clips.find { it.id == clipId } ?: return
        
        val duration = clipToRemove.duration
        
        if (rippleAllTracks) {
            for (i in tracks.indices) {
                val t = tracks[i]
                val currentClips = if (i == trackIndex) t.clips.filter { it.id != clipId } else t.clips
                val newClips = currentClips.map { c ->
                    if (c.timelineIn >= clipToRemove.timelineIn) {
                        c.copy(timelineIn = Math.max(0, c.timelineIn - duration))
                    } else c
                }
                tracks[i] = t.copy(clips = newClips)
            }
        } else {
            val newClips = track.clips.filter { it.id != clipId }.map { c ->
                if (c.timelineIn >= clipToRemove.timelineIn) {
                    c.copy(timelineIn = Math.max(0, c.timelineIn - duration))
                } else c
            }
            tracks[trackIndex] = track.copy(clips = newClips)
        }
        applyState(s.copy(tracks = tracks))
    }
    
    /** Slip Tool: Changes media source IN/OUT points without changing duration or timeline position */
    fun slipClip(trackIndex: Int, clipId: String, deltaFrames: Long) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clip = track.clips.find { it.id == clipId } ?: return
        
        val newSourceIn = Math.max(0, clip.sourceIn + deltaFrames)
        val newSourceOut = newSourceIn + clip.duration
        
        val newClip = clip.copy(sourceIn = newSourceIn, sourceOut = newSourceOut)
        tracks[trackIndex] = track.copy(clips = track.clips.map { if (it.id == clipId) newClip else it })
        applyState(s.copy(tracks = tracks))
    }

    /** Roll Edit: Adjusts the precise cut point between two strictly adjacent clips */
    fun rollEdit(trackIndex: Int, leftClipId: String, rightClipId: String, deltaFrames: Long) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val leftClip = track.clips.find { it.id == leftClipId } ?: return
        val rightClip = track.clips.find { it.id == rightClipId } ?: return
        
        if (leftClip.timelineOut != rightClip.timelineIn) return
        
        val newLeftOut = leftClip.sourceOut + deltaFrames
        val newRightIn = rightClip.sourceIn + deltaFrames
        val newRightTimelineIn = rightClip.timelineIn + deltaFrames
        
        if (newLeftOut <= leftClip.sourceIn || newRightIn >= rightClip.sourceOut) return
        
        val newLeftClip = leftClip.copy(sourceOut = newLeftOut)
        val newRightClip = rightClip.copy(sourceIn = newRightIn, timelineIn = newRightTimelineIn)
        
        tracks[trackIndex] = track.copy(clips = track.clips.map { 
            when(it.id) {
                leftClipId -> newLeftClip
                rightClipId -> newRightClip
                else -> it
            } 
        })
        applyState(s.copy(tracks = tracks))
    }

    fun trimClipStart(trackIndex: Int, clipId: String, newTimelineIn: Long) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clip = track.clips.find { it.id == clipId } ?: return
        
        val deltaFrames = newTimelineIn - clip.timelineIn
        if (deltaFrames == 0L) return
        val newSourceIn = clip.sourceIn + deltaFrames
        if (newSourceIn < 0 || newSourceIn >= clip.sourceOut) return
        
        val newClip = clip.copy(timelineIn = newTimelineIn, sourceIn = newSourceIn)
        tracks[trackIndex] = track.copy(clips = track.clips.map { if (it.id == clipId) newClip else it })
        applyState(s.copy(tracks = tracks))
    }

    fun trimClipEnd(trackIndex: Int, clipId: String, newTimelineOut: Long) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clip = track.clips.find { it.id == clipId } ?: return
        
        val deltaFrames = newTimelineOut - clip.timelineOut
        if (deltaFrames == 0L) return
        val newSourceOut = clip.sourceOut + deltaFrames
        if (newSourceOut <= clip.sourceIn) return
        
        val newClip = clip.copy(sourceOut = newSourceOut)
        tracks[trackIndex] = track.copy(clips = track.clips.map { if (it.id == clipId) newClip else it })
        applyState(s.copy(tracks = tracks))
    }
    fun rippleTrimClipStart(trackIndex: Int, clipId: String, newTimelineIn: Long, rippleAllTracks: Boolean = false) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clip = track.clips.find { it.id == clipId } ?: return
        
        val deltaFrames = newTimelineIn - clip.timelineIn
        if (deltaFrames == 0L) return
        val newSourceIn = clip.sourceIn + deltaFrames
        if (newSourceIn < 0 || newSourceIn >= clip.sourceOut) return
        
        val newClip = clip.copy(sourceIn = newSourceIn) // timelineIn doesn't change in ripple trim start, the clip shortens and everything to the right shifts left. Wait, NLE behavior:
        // Actually, if we ripple trim the IN point:
        // The clip's timelineIn Stays the same, but the sourceIn advances, effectively shortening it. 
        // Then, the clip and everything after it shifts left?
        // No! NLE behavior: The clips to the left don't move. The clip being trimmed has its IN point moved left or right. The entire clip shifts so that its NEW IN point touches the previous clip (or stays where it was). 
        // Let's stick to simple: The clip stays where it is. Its duration changes. Everything to the right shifts.
        // Wait, if I trim the start by +10 frames, it means I cut off 10 frames from the beginning.
        // In ripple trim start, the rest of the clip shifts left by 10 frames to fill the gap. And everything downstream shifts left by 10 frames too.
        // So `newClip` timelineIn stays the same!
        
        if (rippleAllTracks) {
            for (i in tracks.indices) {
                val t = tracks[i]
                var newClips = if (i == trackIndex) t.clips.map { if (it.id == clipId) newClip else it } else t.clips
                newClips = newClips.map { c ->
                    if (c.id != clipId && c.timelineIn >= clip.timelineIn) {
                        c.copy(timelineIn = Math.max(0, c.timelineIn - deltaFrames))
                    } else c
                }
                tracks[i] = t.copy(clips = newClips.sortedBy { it.timelineIn })
            }
        } else {
            var newClips = track.clips.map { if (it.id == clipId) newClip else it }
            newClips = newClips.map { c ->
                if (c.id != clipId && c.timelineIn >= clip.timelineIn) {
                    c.copy(timelineIn = Math.max(0, c.timelineIn - deltaFrames))
                } else c
            }
            tracks[trackIndex] = track.copy(clips = newClips.sortedBy { it.timelineIn })
        }
        applyState(s.copy(tracks = tracks))
    }

    fun rippleTrimClipEnd(trackIndex: Int, clipId: String, newTimelineOut: Long, rippleAllTracks: Boolean = false) {
        val s = _state.value
        val tracks = s.tracks.toMutableList()
        val track = tracks.getOrNull(trackIndex) ?: return
        val clip = track.clips.find { it.id == clipId } ?: return
        
        val deltaFrames = newTimelineOut - clip.timelineOut
        if (deltaFrames == 0L) return
        val newSourceOut = clip.sourceOut + deltaFrames
        if (newSourceOut <= clip.sourceIn) return
        
        val newClip = clip.copy(sourceOut = newSourceOut)
        
        if (rippleAllTracks) {
            for (i in tracks.indices) {
                val t = tracks[i]
                var newClips = if (i == trackIndex) t.clips.map { if (it.id == clipId) newClip else it } else t.clips
                newClips = newClips.map { c ->
                    if (c.id != clipId && c.timelineIn >= clip.timelineOut) {
                        c.copy(timelineIn = Math.max(0, c.timelineIn + deltaFrames))
                    } else c
                }
                tracks[i] = t.copy(clips = newClips.sortedBy { it.timelineIn })
            }
        } else {
            var newClips = track.clips.map { if (it.id == clipId) newClip else it }
            newClips = newClips.map { c ->
                if (c.id != clipId && c.timelineIn >= clip.timelineOut) {
                    c.copy(timelineIn = Math.max(0, c.timelineIn + deltaFrames))
                } else c
            }
            tracks[trackIndex] = track.copy(clips = newClips.sortedBy { it.timelineIn })
        }
        applyState(s.copy(tracks = tracks))
    }
}