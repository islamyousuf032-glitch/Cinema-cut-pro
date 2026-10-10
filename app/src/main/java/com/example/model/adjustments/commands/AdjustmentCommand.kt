package com.example.model.adjustments.commands

import com.example.timeline.core.TimelineCommand
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineResult

interface AdjustmentCommand : TimelineCommand {
    val targetClipId: String
}
