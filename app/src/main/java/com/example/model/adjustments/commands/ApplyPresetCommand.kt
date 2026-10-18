package com.example.model.adjustments.commands

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineResult

class ApplyPresetCommand(
    val presetId: String,
    override val targetClipId: String,
    private val projectBefore: TimelineProject,
    private val projectAfter: TimelineProject
) : AdjustmentCommand {
    override val commandName: String = "Apply Preset"
    override val affectedClipIds: List<String> = listOf(targetClipId)
    override val timestamp: Long = System.currentTimeMillis()

    override fun execute(project: TimelineProject): TimelineResult = TimelineResult(projectAfter)
    override fun undo(project: TimelineProject): TimelineResult = TimelineResult(projectBefore)
    override fun redo(project: TimelineProject): TimelineResult = TimelineResult(projectAfter)
}
