package com.example.timeline.core

class TrimCommand(
    val clipId: String,
    val newFrame: Long,
    val isStart: Boolean,
    val mode: TrimMode = TrimMode.NORMAL,
    override val commandName: String = if (isStart) "Trim Start" else "Trim End",
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {
    private var projectBefore: TimelineProject? = null
    private var projectAfter: TimelineProject? = null
    
    override val affectedClipIds: List<String> = listOf(clipId)

    override fun execute(project: TimelineProject): TimelineResult {
        projectBefore = project
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
