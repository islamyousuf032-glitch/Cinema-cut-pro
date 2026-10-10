package com.example.timeline.core

class RippleTrimCommand(
    val clipId: String,
    val newFrame: Long,
    val isStart: Boolean,
    val rippleAllTracks: Boolean = false,
    override val commandName: String = if (isStart) "Ripple Trim Start" else "Ripple Trim End",
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {
    private var projectBefore: TimelineProject? = null
    private var projectAfter: TimelineProject? = null
    
    override val affectedClipIds: List<String> = listOf(clipId)

    override fun execute(project: TimelineProject): TimelineResult {
        projectBefore = project
        val mode = if (rippleAllTracks) TrimMode.RIPPLE_ALL_TRACKS else TrimMode.RIPPLE
        val result = if (isStart) {
            CoreEditEngine.trimClipStart(project, clipId, newFrame, mode)
        } else {
            CoreEditEngine.trimClipEnd(project, clipId, newFrame, mode)
        }
        
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
