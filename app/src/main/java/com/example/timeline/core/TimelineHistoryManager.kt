package com.example.timeline.core

class TimelineHistoryManager(private val maxHistorySize: Int = 50) {
    private val undoStack = mutableListOf<TimelineCommand>()
    private val redoStack = mutableListOf<TimelineCommand>()
    
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    
    val currentHistoryLabel: String?
        get() = undoStack.lastOrNull()?.commandName

    fun pushCommand(command: TimelineCommand) {
        undoStack.add(command)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }
    
    fun undo(project: TimelineProject): TimelineResult {
        if (!canUndo) return TimelineResult(project, "Nothing to undo")
        val command = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(command)
        return command.undo(project)
    }
    
    fun redo(project: TimelineProject): TimelineResult {
        if (!canRedo) return TimelineResult(project, "Nothing to redo")
        val command = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(command)
        return command.redo(project)
    }
    
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
