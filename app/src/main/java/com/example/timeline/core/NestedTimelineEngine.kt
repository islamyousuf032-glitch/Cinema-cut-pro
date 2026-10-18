package com.example.timeline.core

object NestedTimelineEngine {
    
    fun createSequence(project: TimelineProject, sequence: TimelineSequence): TimelineProject {
        return project.copy(sequences = project.sequences + sequence)
    }
    
    fun deleteSequence(project: TimelineProject, sequenceId: String): TimelineProject {
        return project.copy(sequences = project.sequences.filter { it.id != sequenceId })
    }
    
    fun validateNoRecursiveNesting(project: TimelineProject, targetSequenceId: String, sequenceToInsertId: String): Boolean {
        if (targetSequenceId == sequenceToInsertId) return false
        
        val seq = project.sequences.find { it.id == sequenceToInsertId } ?: return true
        
        val nestedIds = seq.tracks.flatMap { it.clips }.filter { it.type == ClipType.NESTED_SEQUENCE }.map { it.mediaId }
        if (nestedIds.contains(targetSequenceId)) return false
        
        return nestedIds.all { validateNoRecursiveNesting(project, targetSequenceId, it) }
    }

    fun addNestedSequenceClip(
        project: TimelineProject,
        targetTrackId: String,
        sequenceToInsertId: String,
        timelineStart: Long,
        name: String
    ): TimelineResult {
        // Assume project.id is the current top-level sequence for validation
        if (!validateNoRecursiveNesting(project, project.id, sequenceToInsertId)) { 
            return TimelineResult(project, "Recursive nesting detected")
        }
        
        val sequenceToInsert = project.sequences.find { it.id == sequenceToInsertId }
            ?: return TimelineResult(project, "Sequence not found")
            
        val clip = TimelineClip(
            trackId = targetTrackId,
            mediaId = sequenceToInsertId,
            name = name,
            sourceIn = 0,
            sourceOut = sequenceToInsert.duration,
            timelineStart = timelineStart,
            type = ClipType.NESTED_SEQUENCE
        )
        
        return CoreTimelineEngine.addClip(project, targetTrackId, clip)
    }

    fun openNestedSequence(project: TimelineProject, sequenceId: String): TimelineSequence? {
        return project.sequences.find { it.id == sequenceId }
    }
    
    fun updateNestedSequence(project: TimelineProject, updatedSequence: TimelineSequence): TimelineProject {
        return project.copy(sequences = project.sequences.map { if (it.id == updatedSequence.id) updatedSequence else it })
    }
}
