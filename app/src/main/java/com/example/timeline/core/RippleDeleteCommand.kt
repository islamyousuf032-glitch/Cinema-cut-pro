package com.example.timeline.core

class RippleDeleteCommand(
    val clipIds: List<String>,
    val rippleAllTracks: Boolean = false,
    override val commandName: String = "Ripple Delete",
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {
    private var projectBefore: TimelineProject? = null
    private var projectAfter: TimelineProject? = null
    
    override val affectedClipIds: List<String> = clipIds

    override fun execute(project: TimelineProject): TimelineResult {
        projectBefore = project
        val result = CoreEditEngine.deleteClips(project, clipIds, rippleMode = true, rippleAllTracks = rippleAllTracks)
        
        if (result.isSuccess) {
            projectAfter = result.project
        }
        return result
    }

    override fun undo(project: TimelineProject): TimelineResult {
        return TimelineResult(projectBefore ?: project)
    }

    override fun redo(project: TimelineProject): TimelineResult {
        return TimelineResult(projectAfter ?: project)
    }
}
