package com.example.model.colorgrade.commands

import com.example.model.colorgrade.*
import com.example.timeline.core.TimelineCommand
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineResult
import com.example.timeline.core.TimelineClip

// A base command that captures the before/after states of a clip's ColorGradeStack
class UpdateColorGradeCommand(
    private val clipId: String,
    private val oldGrade: ColorGradeStack,
    private val newGrade: ColorGradeStack,
    override val commandName: String = "Color Grade Update"
) : TimelineCommand {
    override val timestamp: Long = System.currentTimeMillis()
    override val affectedClipIds: List<String> = listOf(clipId)

    override fun execute(project: TimelineProject): TimelineResult {
        return applyGrade(project, newGrade)
    }

    override fun undo(project: TimelineProject): TimelineResult {
        return applyGrade(project, oldGrade)
    }

    override fun redo(project: TimelineProject): TimelineResult {
        return applyGrade(project, newGrade)
    }
    
    private fun applyGrade(project: TimelineProject, grade: ColorGradeStack): TimelineResult {
        var found = false
        val newSequences = project.sequences.map { sequence ->
            sequence.copy(
                tracks = sequence.tracks.map { track ->
                    track.copy(
                        clips = track.clips.map { clip ->
                            if (clip.id == clipId) {
                                found = true
                                clip.copy(colorGrade = grade)
                            } else {
                                clip
                            }
                        }
                    )
                }
            )
        }
        
        return if (found) {
            TimelineResult(project.copy(sequences = newSequences), null)
        } else {
            TimelineResult(project, "Clip not found for color grading")
        }
    }
}
