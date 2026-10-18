package com.example.timeline.core

class SnapshotCommand(
    override val commandName: String,
    private val projectBefore: TimelineProject,
    private val projectAfter: TimelineProject,
    override val affectedClipIds: List<String> = emptyList(),
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {

    override fun execute(project: TimelineProject): TimelineResult {
        return TimelineResult(projectAfter)
    }

    override fun undo(project: TimelineProject): TimelineResult {
        return TimelineResult(projectBefore)
    }

    override fun redo(project: TimelineProject): TimelineResult {
        return TimelineResult(projectAfter)
    }
}
