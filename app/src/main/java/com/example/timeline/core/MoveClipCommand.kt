package com.example.timeline.core

class MoveClipCommand(
    val clipId: String,
    val oldStartFrame: Long,
    val newStartFrame: Long,
    val trackId: String,
    override val commandName: String = "Move Clip",
    override val affectedClipIds: List<String> = listOf(clipId),
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {

    override fun execute(project: TimelineProject): TimelineResult {
        return performMove(project, newStartFrame)
    }

    override fun undo(project: TimelineProject): TimelineResult {
        return performMove(project, oldStartFrame)
    }

    override fun redo(project: TimelineProject): TimelineResult {
        return performMove(project, newStartFrame)
    }

    private fun performMove(project: TimelineProject, targetFrame: Long): TimelineResult {
        val clip = project.tracks.flatMap { it.clips }.find { it.id == clipId }
            ?: return TimelineResult(project, "Clip not found")
        val newClip = clip.copy(timelineStart = targetFrame)
        val p1 = CoreEditEngine.deleteClips(project, listOf(clipId)).project
        return CoreTimelineEngine.addClip(p1, trackId, newClip)
    }
}
