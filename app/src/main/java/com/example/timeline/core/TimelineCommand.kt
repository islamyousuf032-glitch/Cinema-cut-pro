package com.example.timeline.core

interface TimelineCommand {
    val commandName: String
    val timestamp: Long
    val affectedClipIds: List<String>
    
    fun execute(project: TimelineProject): TimelineResult
    fun undo(project: TimelineProject): TimelineResult
    fun redo(project: TimelineProject): TimelineResult
}
