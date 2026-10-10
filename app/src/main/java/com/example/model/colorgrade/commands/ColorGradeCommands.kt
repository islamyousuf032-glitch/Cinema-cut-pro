package com.example.model.colorgrade.commands

import com.example.model.colorgrade.*
import com.example.timeline.core.TimelineCommand
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineResult


abstract class BaseColorGradeCommand(
    val clipId: String,
    val oldGrade: ColorGradeStack,
    val newGrade: ColorGradeStack,
    override val commandName: String
) : TimelineCommand {
    override val timestamp: Long = System.currentTimeMillis()
    override val affectedClipIds: List<String> = listOf(clipId)

    override fun execute(project: TimelineProject): TimelineResult = applyGrade(project, newGrade)
    override fun undo(project: TimelineProject): TimelineResult = applyGrade(project, oldGrade)
    override fun redo(project: TimelineProject): TimelineResult = applyGrade(project, newGrade)
    
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
        return if (found) TimelineResult(project.copy(sequences = newSequences), null)
        else TimelineResult(project, "Clip not found for color grading")
    }
}

class ColorGradeCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Color Grade Change")

class SetColorWheelCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Adjust Color Wheel")

class SetCurveCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Adjust Curve")

class AddCurvePointCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Add Curve Point")

class RemoveCurvePointCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Remove Curve Point")

class ApplyLutCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Apply LUT")

class RemoveLutCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Remove LUT")

class SetHslQualifierCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Adjust HSL Qualifier")

class ApplyColorMatchCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Apply Color Match")

class SetSkinProtectionCommand(
    clipId: String,
    oldGrade: ColorGradeStack,
    newGrade: ColorGradeStack
) : BaseColorGradeCommand(clipId, oldGrade, newGrade, "Adjust Skin Protection")
