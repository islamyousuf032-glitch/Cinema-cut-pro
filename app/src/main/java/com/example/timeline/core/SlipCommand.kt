package com.example.timeline.core

class SlipCommand(
    val clipId: String,
    val deltaFrames: Long,
    override val commandName: String = "Slip Edit",
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {
    private var projectBefore: TimelineProject? = null
    private var projectAfter: TimelineProject? = null
    
    override val affectedClipIds: List<String> = listOf(clipId)

    override fun execute(project: TimelineProject): TimelineResult {
        projectBefore = project
        val result = CoreEditEngine.slipEdit(project, clipId, deltaFrames)
        
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
