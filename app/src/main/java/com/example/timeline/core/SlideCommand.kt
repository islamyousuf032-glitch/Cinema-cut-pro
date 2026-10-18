package com.example.timeline.core

class SlideCommand(
    val clipId: String,
    val deltaFrames: Long,
    override val commandName: String = "Slide Edit",
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {
    private var projectBefore: TimelineProject? = null
    private var projectAfter: TimelineProject? = null
    
    override val affectedClipIds: List<String> = listOf(clipId)

    override fun execute(project: TimelineProject): TimelineResult {
        projectBefore = project
        val result = CoreEditEngine.slideEdit(project, clipId, deltaFrames)
        
        if (result.isSuccess) {
            projectAfter = result.project
            // Compute actually affected clip IDs based on adjacency
            // But we can just use the whole track or keep simple
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
