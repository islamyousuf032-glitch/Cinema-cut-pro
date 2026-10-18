package com.example.timeline.core

class CompositeTimelineCommand(
    override val commandName: String,
    private val commands: List<TimelineCommand>,
    override val timestamp: Long = System.currentTimeMillis()
) : TimelineCommand {

    override val affectedClipIds: List<String>
        get() = commands.flatMap { it.affectedClipIds }.distinct()

    override fun execute(project: TimelineProject): TimelineResult {
        var currentProject = project
        for (cmd in commands) {
            val res = cmd.execute(currentProject)
            if (!res.isSuccess) return res
            currentProject = res.project
        }
        return TimelineResult(currentProject)
    }

    override fun undo(project: TimelineProject): TimelineResult {
        var currentProject = project
        for (cmd in commands.reversed()) {
            val res = cmd.undo(currentProject)
            if (!res.isSuccess) return res
            currentProject = res.project
        }
        return TimelineResult(currentProject)
    }

    override fun redo(project: TimelineProject): TimelineResult {
        var currentProject = project
        for (cmd in commands) {
            val res = cmd.redo(currentProject)
            if (!res.isSuccess) return res
            currentProject = res.project
        }
        return TimelineResult(currentProject)
    }
}
