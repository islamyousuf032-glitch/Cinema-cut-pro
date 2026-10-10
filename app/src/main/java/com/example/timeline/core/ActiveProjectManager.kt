package com.example.timeline.core

object ActiveProjectManager {
    var activeProject: TimelineProject? = null
    var playheadFrame: Long = 0L
    var selectedClipId: String? = null
    
    // Set to true when returning from AdvancedTimelineActivity to notify MainActivity
    var isDirty: Boolean = false
}
