package com.example.timeline.core

object CommandFactory {

    fun createTrimStartCommand(
        projectBefore: TimelineProject,
        clipId: String,
        newStartFrame: Long,
        mode: TrimMode = TrimMode.NORMAL
    ): TimelineCommand? {
        val result = CoreEditEngine.trimClipStart(projectBefore, clipId, newStartFrame, mode)
        if (!result.isSuccess) return null
        return TrimCommand(
            clipId = clipId,
            newFrame = newStartFrame,
            isStart = true,
            mode = mode
        )
    }

    fun createTrimEndCommand(
        projectBefore: TimelineProject,
        clipId: String,
        newEndFrame: Long,
        mode: TrimMode = TrimMode.NORMAL
    ): TimelineCommand? {
        val result = CoreEditEngine.trimClipEnd(projectBefore, clipId, newEndFrame, mode)
        if (!result.isSuccess) return null
        return TrimCommand(
            clipId = clipId,
            newFrame = newEndFrame,
            isStart = false,
            mode = mode
        )
    }
    
    fun createSplitCommand(
       projectBefore: TimelineProject,
       clipId: String,
       splitFrame: Long
    ): TimelineCommand? {
        val result = CoreEditEngine.splitClipAtFrame(projectBefore, clipId, splitFrame)
        if (!result.isSuccess) return null
        return SnapshotCommand(
            commandName = "Split Clip",
            projectBefore = projectBefore,
            projectAfter = result.project,
            affectedClipIds = listOf(clipId) + result.newClipIds
        )
    }

    fun createRippleDeleteCommand(
        projectBefore: TimelineProject,
        clipIds: List<String>
    ): TimelineCommand? {
        val result = CoreEditEngine.deleteClips(projectBefore, clipIds, rippleMode = true)
        if (!result.isSuccess) return null
        return SnapshotCommand(
            commandName = "Ripple Delete",
            projectBefore = projectBefore,
            projectAfter = result.project,
            affectedClipIds = clipIds
        )
    }
    
    fun createCompoundClipCommand(
        projectBefore: TimelineProject,
        clipIds: List<String>,
        newSequenceName: String,
        destTrackId: String
    ): TimelineCommand? {
        val result = CompoundClipEngine.compoundClipFromSelection(projectBefore, clipIds, newSequenceName, destTrackId)
        if (!result.isSuccess) return null
        return SnapshotCommand(
             commandName = "Create Compound Clip",
             projectBefore = projectBefore,
             projectAfter = result.project,
             affectedClipIds = clipIds
        )
    }

    fun createAddClipCommand(
        project: TimelineProject,
        trackId: String,
        clip: TimelineClip
    ): TimelineCommand {
        return object : TimelineCommand {
            override val commandName = "Add Clip"
            override val timestamp = System.currentTimeMillis()
            override val affectedClipIds = listOf(clip.id)
            
            override fun execute(p: TimelineProject): TimelineResult {
                return CoreTimelineEngine.addClip(p, trackId, clip)
            }
            override fun undo(p: TimelineProject): TimelineResult {
                return CoreEditEngine.deleteClips(p, listOf(clip.id))
            }
            override fun redo(p: TimelineProject): TimelineResult {
                return CoreTimelineEngine.addClip(p, trackId, clip)
            }
        }
    }
}
